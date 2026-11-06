package com.ecomm.supportassistant.adapter.out.claude;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.ErrorType;
import com.ecomm.supportassistant.application.port.out.RateLimiter;
import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.application.port.out.TimeSource;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.IllegalStateTransitionException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every call to the provider, guarded. The circuit breaker lets it through unless it is open; then
 * the provider ceiling, shared by every instance through Redis, lets it through unless the service
 * has asked as often as it may this minute ({@link SupportModel.BusyException}). A call the
 * provider may get over, as below, is retried within the breaker, after the wait the provider asks
 * for or half a second; a retry is a call against the ceiling like any other. Each call is given
 * its own timeout or what is left of the turn, whichever is shorter, and one cut off by the turn's
 * time, or whose retry would come after it, is {@link SupportModel.OutOfTimeException}, which the
 * breaker doesn't count.
 *
 * <p>A rate limit (429), an overload (529), any other 5xx, a timeout or a connection failure counts
 * against the breaker, which opens at half its window failed or at {@code failuresInARow} in a row.
 * Any other refusal, such as a 400 or 401, is a bug or a bad key: logged with the provider's error
 * type and not counted. Either is {@link SupportModel.UnavailableException}, as is a call the open
 * breaker refuses.
 *
 * <p>It reports {@code support_assistant_provider_errors_total} by kind and {@code
 * support_assistant_provider_breaker_state} by state, 1 for the breaker's current one, each from
 * the start.
 */
public class ProviderCalls {

  private static final Logger log = LoggerFactory.getLogger(ProviderCalls.class);

  /** What went wrong with a call, as the metrics name it. */
  enum ErrorKind {
    RATE_LIMITED(true),
    OVERLOADED(true),
    SERVER_ERROR(true),
    TIMEOUT(true),
    CONNECTION(true),
    BAD_REQUEST(false),
    UNAUTHORIZED(false),
    OTHER(false);

    /** Whether it counts against the breaker: the provider's trouble, not the request's. */
    final boolean counts;

    ErrorKind(boolean counts) {
      this.counts = counts;
    }

    static ErrorKind of(Throwable e) {
      if (e instanceof AnthropicServiceException refused) {
        return switch (refused.statusCode()) {
          case 400 -> BAD_REQUEST;
          case 401 -> UNAUTHORIZED;
          case 429 -> RATE_LIMITED;
          case 529 -> OVERLOADED;
          default -> refused.statusCode() >= 500 ? SERVER_ERROR : OTHER;
        };
      }
      if (e instanceof AnthropicIoException) {
        for (var cause = e.getCause(); cause != null; cause = cause.getCause()) {
          if (cause instanceof InterruptedIOException) {
            return TIMEOUT;
          }
        }
        return CONNECTION;
      }
      return OTHER;
    }
  }

  /** Whether {@code e} counts against the breaker. */
  public static final Predicate<Throwable> COUNTS =
      e -> e instanceof AnthropicException && ErrorKind.of(e).counts;

  /** How long to wait before a retry when the provider doesn't say. */
  private static final Duration RETRY_WAIT = Duration.ofMillis(500);

  private final CircuitBreaker breaker;
  private final int failuresInARow;
  private final Duration callTimeout;
  private final int retries;
  private final RateLimiter rateLimiter;
  private final RateLimiter.Limit ceiling;
  private final TimeSource time;
  private final Map<ErrorKind, Counter> errors = new EnumMap<>(ErrorKind.class);
  private final AtomicInteger inARow = new AtomicInteger();

  /**
   * @param breaker counts the calls that fail as {@link #COUNTS} says, and ignores the rest
   * @param failuresInARow how many failures in a row open the breaker, however full its window
   * @param callTimeout how long one call may take, at most
   * @param retries how many times a call that failed for the provider's trouble is made again
   * @param ceiling how many calls the service may make, across its instances
   */
  public ProviderCalls(
      CircuitBreaker breaker,
      int failuresInARow,
      Duration callTimeout,
      int retries,
      RateLimiter rateLimiter,
      RateLimiter.Limit ceiling,
      TimeSource time,
      MeterRegistry meters) {
    this.breaker = breaker;
    this.failuresInARow = failuresInARow;
    this.callTimeout = callTimeout;
    this.retries = retries;
    this.rateLimiter = rateLimiter;
    this.ceiling = ceiling;
    this.time = time;
    // A run of failures starts afresh whenever the breaker changes state.
    breaker.getEventPublisher().onStateTransition(e -> inARow.set(0)).onReset(e -> inARow.set(0));
    for (var kind : ErrorKind.values()) {
      errors.put(
          kind,
          Counter.builder("support_assistant.provider.errors")
              .description("Calls to the provider that failed, by why")
              .tag("kind", name(kind))
              .register(meters));
    }
    for (var state :
        List.of(
            CircuitBreaker.State.CLOSED,
            CircuitBreaker.State.OPEN,
            CircuitBreaker.State.HALF_OPEN)) {
      Gauge.builder(
              "support_assistant.provider.breaker.state",
              breaker,
              b -> b.getState() == state ? 1 : 0)
          .description("1 for the state the circuit breaker around the provider is in")
          .tag("state", name(state))
          .register(meters);
    }
  }

  /**
   * Makes the call, given its timeout, if the breaker and the ceiling let it through, and makes it
   * again if it fails for the provider's trouble, all within {@code timeLeft}.
   */
  public <T> T call(Function<Duration, T> call, Duration timeLeft) {
    var deadline = System.nanoTime() + timeLeft.toNanos();
    try {
      var answer = breaker.executeSupplier(() -> attempts(call, deadline));
      inARow.set(0);
      return answer;
    } catch (CallNotPermittedException e) {
      throw new SupportModel.UnavailableException("The circuit breaker is open", e);
    } catch (AnthropicException e) {
      throw failed(e);
    }
  }

  private <T> T attempts(Function<Duration, T> call, long deadline) {
    for (var attempt = 0; ; attempt++) {
      var left = Duration.ofNanos(deadline - System.nanoTime());
      if (!left.isPositive()) {
        throw new SupportModel.OutOfTimeException(null);
      }
      rateLimiter
          .take("provider", List.of(ceiling), time.now())
          .ifPresent(
              refusal -> {
                throw new SupportModel.BusyException(refusal.retryAfter());
              });
      var cutByTheTurn = left.compareTo(callTimeout) < 0;
      try {
        return call.apply(cutByTheTurn ? left : callTimeout);
      } catch (AnthropicException e) {
        var kind = ErrorKind.of(e);
        if (kind == ErrorKind.TIMEOUT && cutByTheTurn) {
          throw new SupportModel.OutOfTimeException(e);
        }
        var wait = waitBeforeRetrying(e);
        if (!kind.counts || attempt == retries || wait.toNanos() >= deadline - System.nanoTime()) {
          throw e;
        }
        // The last failure is counted as the call's; one retried, here.
        errors.get(kind).increment();
        log.warn("The provider failed: {}; asking again in {} ms", name(kind), wait.toMillis());
        sleep(wait, e);
      }
    }
  }

  /** The wait the provider asks for, in {@code retry-after-ms} or {@code retry-after}, or ours. */
  private static Duration waitBeforeRetrying(AnthropicException e) {
    if (e instanceof AnthropicServiceException refused) {
      try {
        var millis = refused.headers().values("retry-after-ms");
        if (!millis.isEmpty()) {
          return Duration.ofMillis((long) Double.parseDouble(millis.getFirst()));
        }
        var seconds = refused.headers().values("retry-after");
        if (!seconds.isEmpty()) {
          return Duration.ofSeconds(Long.parseLong(seconds.getFirst().strip()));
        }
      } catch (NumberFormatException notANumber) {
        // An HTTP date, or nothing readable: our own wait.
      }
    }
    return RETRY_WAIT;
  }

  private static void sleep(Duration wait, AnthropicException failure) {
    try {
      Thread.sleep(wait);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw failure;
    }
  }

  private SupportModel.UnavailableException failed(AnthropicException e) {
    var kind = ErrorKind.of(e);
    errors.get(kind).increment();
    if (kind.counts) {
      log.warn("The provider failed: {}", name(kind));
      if (inARow.incrementAndGet() >= failuresInARow) {
        open();
      }
    } else {
      var type =
          e instanceof AnthropicServiceException refused
              ? refused.errorType().map(ErrorType::asString).orElse("unknown")
                  + " ("
                  + refused.statusCode()
                  + ")"
              : e.getClass().getSimpleName();
      log.error("The provider refused the request as {}: a bug or a bad API key", type);
    }
    return new SupportModel.UnavailableException("The provider failed: " + name(kind), e);
  }

  private void open() {
    try {
      breaker.transitionToOpenState();
    } catch (IllegalStateTransitionException alreadyOpen) {
      // Another call's failure opened it first.
    }
  }

  private static String name(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }
}
