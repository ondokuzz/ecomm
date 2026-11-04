package com.ecomm.supportassistant.adapter.out.mock;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the provider at start-up, and says which. With the secret {@code anthropic-api-key}
 * empty, as {@code make secrets} writes it, the mock provider answers.
 */
@Configuration
class ProviderConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ProviderConfiguration.class);

  @Bean
  SupportModel supportModel(@Value("${ecomm.support-assistant.anthropic-api-key}") String apiKey) {
    if (apiKey.isBlank()) {
      log.info("No Anthropic API key: the Support Assistant answers with the mock provider");
    } else {
      // The key is never logged.
      log.warn(
          "An Anthropic API key is set, but this build has no Claude adapter yet: the Support"
              + " Assistant answers with the mock provider");
    }
    return new MockSupportModel();
  }
}
