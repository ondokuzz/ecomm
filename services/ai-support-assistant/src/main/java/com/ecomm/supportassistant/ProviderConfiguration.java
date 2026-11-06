package com.ecomm.supportassistant;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.ecomm.supportassistant.adapter.out.claude.ClaudeSupportModel;
import com.ecomm.supportassistant.adapter.out.claude.ProviderCalls;
import com.ecomm.supportassistant.adapter.out.mock.MockSupportModel;
import com.ecomm.supportassistant.application.port.out.RateLimiter;
import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.application.port.out.TimeSource;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the provider at start-up, and says which (ADR 0003). With the secret {@code
 * anthropic-api-key} empty, as {@code make secrets} writes it, the mock provider answers; with a
 * key, Claude, behind the circuit breaker and the provider ceiling. Neither guards the mock, which
 * has no key's rate limit or budget to protect.
 */
@Configuration
class ProviderConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ProviderConfiguration.class);

  @Bean
  SupportModel supportModel(
      @Value("${ecomm.support-assistant.anthropic-api-key}") String apiKey,
      @Value("${ecomm.support-assistant.anthropic-base-url}") String baseUrl,
      @Value("${ecomm.support-assistant.anthropic-model}") String model,
      @Value("${ecomm.support-assistant.storefront-url}") String storefrontUrl,
      ProviderCalls calls) {
    if (apiKey.isBlank()) {
      log.info("No Anthropic API key: the Support Assistant answers with the mock provider");
      return new MockSupportModel();
    }
    // The key is never logged.
    log.info("An Anthropic API key is set: the Support Assistant answers with Claude ({})", model);
    var client =
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey.strip())
            .baseUrl(baseUrl)
            // ProviderCalls retries, so that each retry counts against the ceiling, and times out
            // each call.
            .maxRetries(0)
            .build();
    return new ClaudeSupportModel(client, calls, model, storefrontUrl);
  }

  /**
   * Opens when half the calls in its window failed, once the window is full, and lets one call
   * through after its wait. {@link ProviderCalls} opens it too after a run of failures.
   */
  @Bean
  CircuitBreaker providerBreaker(
      @Value("${ecomm.support-assistant.breaker.window}") int window,
      @Value("${ecomm.support-assistant.breaker.failure-rate}") float failureRate,
      @Value("${ecomm.support-assistant.breaker.wait-when-open}") Duration waitWhenOpen) {
    return CircuitBreaker.of(
        "provider",
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(window)
            .minimumNumberOfCalls(window)
            .failureRateThreshold(failureRate)
            .waitDurationInOpenState(waitWhenOpen)
            .permittedNumberOfCallsInHalfOpenState(1)
            .recordException(ProviderCalls.COUNTS)
            .ignoreException(ProviderCalls.COUNTS.negate())
            .build());
  }

  /** Made with or without a key, so its metrics are there from the start either way. */
  @Bean
  ProviderCalls providerCalls(
      CircuitBreaker providerBreaker,
      @Value("${ecomm.support-assistant.breaker.failures-in-a-row}") int failuresInARow,
      @Value("${ecomm.support-assistant.anthropic-timeout}") Duration callTimeout,
      @Value("${ecomm.support-assistant.anthropic-max-retries}") int retries,
      @Value("${ecomm.support-assistant.provider-calls-per-minute}") int callsPerMinute,
      RateLimiter rateLimiter,
      TimeSource time,
      MeterRegistry meters) {
    return new ProviderCalls(
        providerBreaker,
        failuresInARow,
        callTimeout,
        retries,
        rateLimiter,
        new RateLimiter.Limit(callsPerMinute, Duration.ofMinutes(1)),
        time,
        meters);
  }
}
