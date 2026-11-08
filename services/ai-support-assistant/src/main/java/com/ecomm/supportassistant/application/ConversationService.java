package com.ecomm.supportassistant.application;

import com.ecomm.supportassistant.application.port.in.AssistantBusyException;
import com.ecomm.supportassistant.application.port.in.AssistantRateLimitedException;
import com.ecomm.supportassistant.application.port.in.AssistantUnavailableException;
import com.ecomm.supportassistant.application.port.in.ConversationBusyException;
import com.ecomm.supportassistant.application.port.in.ConversationNotFoundException;
import com.ecomm.supportassistant.application.port.in.ConversationsUseCase;
import com.ecomm.supportassistant.application.port.in.IdempotencyKeyInFlightException;
import com.ecomm.supportassistant.application.port.out.AssistantMetrics;
import com.ecomm.supportassistant.application.port.out.ConversationRepository;
import com.ecomm.supportassistant.application.port.out.FulfillmentShippingPort;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort;
import com.ecomm.supportassistant.application.port.out.PaymentPort;
import com.ecomm.supportassistant.application.port.out.RateLimiter;
import com.ecomm.supportassistant.application.port.out.ReturnsWarrantyPort;
import com.ecomm.supportassistant.application.port.out.SearchDiscoveryPort;
import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.application.port.out.TimeSource;
import com.ecomm.supportassistant.application.port.out.TurnsInFlight;
import com.ecomm.supportassistant.domain.Conversation;
import com.ecomm.supportassistant.domain.Entry;
import com.ecomm.supportassistant.domain.Reference;
import com.ecomm.supportassistant.domain.ToolCall;
import com.ecomm.supportassistant.domain.ToolResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a Customer's Conversations. A turn adds the Customer's message, then asks the model and runs
 * the lookups it asks for, one answer at a time, until it replies. The lookups of one answer run at
 * once, and their results go back to the model together. The reply's references are the records the
 * turn's lookups returned that its text names, so a link always points to a real record of the
 * Customer's, never to one the model made up.
 */
public class ConversationService implements ConversationsUseCase {

  private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

  /** The reply to a turn that reached its bounds before the provider replied. */
  static final String TOO_MUCH =
      "I'm sorry, that was too much to look up at once. Could you ask about one thing at a time?";

  private final ConversationRepository conversations;
  private final SupportModel model;
  private final SupportTools tools;
  private final Executor lookups;
  private final TimeSource time;
  private final Duration lapsesAfter;
  private final RateLimiter rateLimiter;
  private final List<RateLimiter.Limit> customerLimits;
  private final Limits limits;
  private final AssistantMetrics metrics;
  private final TurnsInFlight inFlight;

  /**
   * How long past the turn's time a claim on its Conversation is kept, should it not be released.
   */
  private static final Duration HELD_PAST = Duration.ofMinutes(1);

  /**
   * How much a Customer, a turn and the service may ask of the assistant.
   *
   * @param messagesPerHour how many messages a Customer may send in any hour
   * @param messagesPerDay how many in any day
   * @param providerCallsPerTurn how many times a turn may ask the provider
   * @param turnTimeLimit how long a turn may run before it stops asking
   */
  public record Limits(
      int messagesPerHour, int messagesPerDay, int providerCallsPerTurn, Duration turnTimeLimit) {}

  /**
   * @param lookups runs each of an answer's lookups when it asks for more than one, with the
   *     request thread's Customer token and Correlation ID
   * @param lapsesAfter how long a Conversation is kept after its last message
   * @param rateLimiter counts each Customer's messages against {@code limits}
   * @param metrics counts the tokens, the turns and the refusals
   * @param inFlight holds each Conversation while a turn of its runs
   */
  public ConversationService(
      ConversationRepository conversations,
      SupportModel model,
      OrderManagementPort orderManagement,
      FulfillmentShippingPort fulfillmentShipping,
      PaymentPort payment,
      ReturnsWarrantyPort returnsWarranty,
      SearchDiscoveryPort searchDiscovery,
      Executor lookups,
      TimeSource time,
      Duration lapsesAfter,
      RateLimiter rateLimiter,
      Limits limits,
      AssistantMetrics metrics,
      TurnsInFlight inFlight) {
    this.conversations = conversations;
    this.model = model;
    this.tools =
        new SupportTools(
            orderManagement, fulfillmentShipping, payment, returnsWarranty, searchDiscovery);
    this.lookups = lookups;
    this.time = time;
    this.lapsesAfter = lapsesAfter;
    this.rateLimiter = rateLimiter;
    this.limits = limits;
    this.metrics = metrics;
    this.inFlight = inFlight;
    this.customerLimits =
        List.of(
            new RateLimiter.Limit(limits.messagesPerHour(), Duration.ofHours(1)),
            new RateLimiter.Limit(limits.messagesPerDay(), Duration.ofDays(1)));
  }

  @Override
  public Conversation start(String customerId) {
    var conversation = Conversation.start(UUID.randomUUID().toString(), customerId, time.now());
    conversations.create(conversation, lapsesAfter);
    return conversation;
  }

  @Override
  public Sent send(String customerId, String conversationId, String text, String idempotencyKey) {
    // Another Customer's Conversation is a 404 before it is claimed, and an answered key replays
    // even while another message's turn runs.
    var replied = replayed(conversation(customerId, conversationId), text, idempotencyKey);
    if (replied.isPresent()) {
      return replied.get();
    }
    // The Conversation is held while the turn runs, so it runs one turn at a time, and a turn
    // always starts from the last one's save.
    var held =
        switch (inFlight.claim(
            conversationId, idempotencyKey, limits.turnTimeLimit().plus(HELD_PAST))) {
          case TurnsInFlight.Claim.Held claim -> claim;
          case TurnsInFlight.Claim.HeldBySameKey ignored ->
              throw new IdempotencyKeyInFlightException();
          case TurnsInFlight.Claim.HeldByAnother ignored -> throw new ConversationBusyException();
        };
    try {
      var conversation = conversation(customerId, conversationId);
      // The message may have been answered, and its claim released, since the look above.
      return replayed(conversation, text, idempotencyKey)
          .orElseGet(() -> answer(conversation, text, idempotencyKey));
    } finally {
      inFlight.release(held);
    }
  }

  /** The reply to the message sent with {@code idempotencyKey}, once it has one; none without. */
  private Optional<Sent> replayed(Conversation conversation, String text, String idempotencyKey) {
    if (idempotencyKey == null) {
      return Optional.empty();
    }
    var conversationId = conversation.id();
    var replied = conversation.replyTo(idempotencyKey, text);
    replied.ifPresent(
        reply ->
            log.info(
                "Replayed the reply in Conversation {} for its Idempotency-Key", conversationId));
    return replied.map(reply -> new Sent(reply, true));
  }

  /** Adds the Customer's message, counts it against their limits, and runs its turn. */
  private Sent answer(Conversation conversation, String text, String idempotencyKey) {
    var customerId = conversation.customerId();
    conversation = conversation.withCustomerMessage(text, idempotencyKey, time.now());
    // Only a message the Conversation takes counts, and it counts before the provider is asked.
    rateLimiter
        .take("customer:" + customerId, customerLimits, time.now())
        .ifPresent(
            refusal -> {
              metrics.refused(
                  refusal.limit().equals(customerLimits.getFirst())
                      ? AssistantMetrics.Limit.MESSAGES_PER_HOUR
                      : AssistantMetrics.Limit.MESSAGES_PER_DAY);
              throw new AssistantRateLimitedException(refusal.retryAfter());
            });
    return new Sent(turn(conversation), false);
  }

  /**
   * Asks the model, and runs the lookups it asks for, until it replies or the turn reaches its
   * bounds: the reply, saved with the Conversation if it is still kept.
   *
   * @throws ConversationNotFoundException if the Conversation was ended, or lapsed, meanwhile
   */
  private Entry.AssistantReply turn(Conversation conversation) {
    var started = System.nanoTime();
    var results = new ArrayList<ToolResult>();
    var calls = 0;
    var usage = SupportModel.Usage.NONE;
    var outcome = AssistantMetrics.Outcome.FAILED;
    try {
      while (true) {
        var timeLeft = limits.turnTimeLimit().minus(Duration.ofNanos(System.nanoTime() - started));
        // A model looking things up on and on ends here, with the lookups it had so far.
        if (calls == limits.providerCallsPerTurn() || !timeLeft.isPositive()) {
          outcome = AssistantMetrics.Outcome.BOUNDED;
          return bounded(conversation);
        }
        calls++;
        SupportModel.Answer answer;
        try {
          answer =
              model.answer(
                  new SupportModel.Request(
                      SystemPrompt.TEXT,
                      SupportTools.DEFINITIONS,
                      conversation.entries(),
                      timeLeft));
        } catch (SupportModel.OutOfTimeException e) {
          outcome = AssistantMetrics.Outcome.BOUNDED;
          return bounded(conversation);
        }
        usage = usage.plus(answer.usage());
        metrics.tokens(answer.usage());
        switch (answer) {
          case SupportModel.ToolUse(var toolCalls, var ignored, var providerContent) -> {
            var turnResults = runAtOnce(toolCalls);
            results.addAll(turnResults);
            conversation =
                conversation.withToolUse(toolCalls, providerContent).withToolResults(turnResults);
          }
          case SupportModel.Reply(var replyText, var ignored, var providerContent) -> {
            var reply =
                new Entry.AssistantReply(
                    replyText, referencesNamedIn(replyText, results), time.now(), providerContent);
            saveIfStillKept(conversation.withReply(reply));
            outcome = AssistantMetrics.Outcome.REPLIED;
            return reply;
          }
        }
      }
    } catch (ConversationNotFoundException e) {
      // Ended, or lapsed, while the turn ran: it stays gone, though its lookups ran and its message
      // counted.
      outcome = AssistantMetrics.Outcome.GONE;
      throw e;
    } catch (SupportModel.UnavailableException e) {
      // The Conversation is left as it was before the message, which the Customer can send again.
      outcome = AssistantMetrics.Outcome.UNAVAILABLE;
      throw new AssistantUnavailableException(e);
    } catch (SupportModel.BusyException e) {
      outcome = AssistantMetrics.Outcome.BUSY;
      metrics.refused(AssistantMetrics.Limit.PROVIDER_CALLS_PER_MINUTE);
      throw new AssistantBusyException(e.retryAfter());
    } finally {
      var took = Duration.ofNanos(System.nanoTime() - started);
      metrics.turn(outcome, took);
      // A turn that failed is logged too. The Correlation ID is in the MDC; the Customer's words
      // and the reply stay out.
      log.info(
          "Turn in Conversation {} {}: tools [{}], {} provider calls, tokens {} in, {} out, {}"
              + " cache read, in {} ms",
          conversation.id(),
          outcome.name().toLowerCase(Locale.ROOT),
          results.stream()
              .map(r -> r.tool() + " " + r.outcome().name().toLowerCase(Locale.ROOT))
              .collect(Collectors.joining(", ")),
          calls,
          usage.input(),
          usage.output(),
          usage.cacheRead(),
          took.toMillis());
    }
  }

  /** The reply to a turn that reached its bounds, saved after the lookups it made. */
  private Entry.AssistantReply bounded(Conversation conversation) {
    var reply = new Entry.AssistantReply(TOO_MUCH, List.of(), time.now(), null);
    saveIfStillKept(conversation.withReply(reply));
    return reply;
  }

  /** Saves the Conversation at the end of its turn, if it is still kept. */
  private void saveIfStillKept(Conversation conversation) {
    if (!conversations.replace(conversation, lapsesAfter)) {
      throw new ConversationNotFoundException(conversation.id());
    }
  }

  /** Runs the lookups of one answer, at once when there are several: their results, in order. */
  private List<ToolResult> runAtOnce(List<ToolCall> calls) {
    if (calls.size() == 1) {
      return List.of(tools.run(calls.getFirst()));
    }
    var running =
        calls.stream()
            .map(call -> CompletableFuture.supplyAsync(() -> tools.run(call), lookups))
            .toList();
    return running.stream().map(CompletableFuture::join).toList();
  }

  /**
   * The records {@code results} returned that {@code text} names, each once, in order. A name only
   * part of a longer one the text names doesn't count: "Google Pixel 9 Pro" names that Product, not
   * the Google Pixel 9 as well.
   */
  private static List<Reference> referencesNamedIn(String text, List<ToolResult> results) {
    var records = results.stream().flatMap(r -> r.records().stream()).toList();
    var names = records.stream().flatMap(r -> namesOf(r).stream()).distinct().toList();
    var byId = new LinkedHashMap<String, Reference>();
    records.stream()
        .filter(record -> isNamedIn(text, record, names))
        .forEach(record -> byId.putIfAbsent(record.id(), record));
    return List.copyOf(byId.values());
  }

  /**
   * What the record may be named by: its reference, without an Order reference's {@code #}, and a
   * Product's name.
   */
  private static List<String> namesOf(Reference record) {
    var reference = record.reference().replaceFirst("^#", "");
    return record.kind() == Reference.Kind.PRODUCT
        ? List.of(reference, record.label())
        : List.of(reference);
  }

  /**
   * Whether {@code text} names the record, in either case, by a whole word or words not within a
   * longer one of {@code names}.
   */
  private static boolean isNamedIn(String text, Reference record, List<String> names) {
    for (var name : namesOf(record)) {
      var matcher = whole(name).matcher(text);
      while (matcher.find()) {
        var start = matcher.start();
        var end = matcher.end();
        var withinLonger =
            names.stream()
                .filter(n -> n.length() > name.length())
                .anyMatch(
                    n ->
                        whole(n)
                            .matcher(text)
                            .results()
                            .anyMatch(m -> m.start() <= start && m.end() >= end));
        if (!withinLonger) {
          return true;
        }
      }
    }
    return false;
  }

  private static Pattern whole(String name) {
    return Pattern.compile(
        "(?<![0-9A-Za-z])" + Pattern.quote(name) + "(?![0-9A-Za-z])", Pattern.CASE_INSENSITIVE);
  }

  @Override
  public Conversation conversation(String customerId, String conversationId) {
    return conversations
        .find(conversationId)
        .filter(c -> c.customerId().equals(customerId))
        .orElseThrow(() -> new ConversationNotFoundException(conversationId));
  }

  @Override
  public void end(String customerId, String conversationId) {
    conversations.delete(conversation(customerId, conversationId).id());
  }
}
