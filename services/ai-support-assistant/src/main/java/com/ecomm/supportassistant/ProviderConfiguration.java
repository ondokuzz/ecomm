package com.ecomm.supportassistant;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.ecomm.supportassistant.adapter.out.claude.ClaudeSupportModel;
import com.ecomm.supportassistant.adapter.out.mock.MockSupportModel;
import com.ecomm.supportassistant.application.port.out.SupportModel;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the provider at start-up, and says which (ADR 0003). With the secret {@code
 * anthropic-api-key} empty, as {@code make secrets} writes it, the mock provider answers; with a
 * key, Claude.
 */
@Configuration
class ProviderConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ProviderConfiguration.class);

  @Bean
  SupportModel supportModel(
      @Value("${ecomm.support-assistant.anthropic-api-key}") String apiKey,
      @Value("${ecomm.support-assistant.anthropic-base-url}") String baseUrl,
      @Value("${ecomm.support-assistant.anthropic-model}") String model,
      @Value("${ecomm.support-assistant.anthropic-timeout}") Duration timeout,
      @Value("${ecomm.support-assistant.anthropic-max-retries}") int maxRetries,
      @Value("${ecomm.support-assistant.storefront-url}") String storefrontUrl) {
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
            .timeout(timeout)
            .maxRetries(maxRetries)
            .build();
    return new ClaudeSupportModel(client, model, storefrontUrl);
  }
}
