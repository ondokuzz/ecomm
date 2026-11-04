package com.ecomm.supportassistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * The service says at start-up which provider answers: the mock while the API key is empty, as
 * {@code make secrets} writes it. A key is never logged.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProviderStartUpTest {

  @Test
  void withNoApiKeyTheMockProviderAnswers(CapturedOutput output) {
    try (var app = start("")) {
      assertThat(output)
          .contains("No Anthropic API key: the Support Assistant answers with the mock provider");
    }
  }

  @Test
  void anApiKeyIsNeverLogged(CapturedOutput output) {
    try (var app = start("sk-ant-test-key-1")) {
      assertThat(output)
          .contains("An Anthropic API key is set")
          .doesNotContain("sk-ant-test-key-1");
    }
  }

  /** The service, on a random port; it reaches neither Redis nor Keycloak until asked. */
  private static org.springframework.context.ConfigurableApplicationContext start(String apiKey) {
    return new SpringApplicationBuilder(SupportAssistantApplication.class)
        .run(
            "--server.port=0",
            "--ecomm.support-assistant.anthropic-api-key=" + apiKey,
            "--ecomm.security.jwt.jwk-set-uri=http://localhost:1/certs");
  }
}
