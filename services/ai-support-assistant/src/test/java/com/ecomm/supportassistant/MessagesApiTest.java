package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for tests with an API key, so Claude answers, through the Messages API, which a WireMock
 * server stands in for: it scripts the model's side, answer by answer, and keeps every request the
 * service sent it.
 */
abstract class MessagesApiTest extends SupportAssistantApiTest {

  static final String API_KEY = "sk-ant-test-key-2";

  static final WireMockServer MESSAGES_API = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    MESSAGES_API.start();
  }

  @DynamicPropertySource
  static void claude(DynamicPropertyRegistry registry) {
    registry.add("ecomm.support-assistant.anthropic-api-key", () -> API_KEY);
    registry.add("ecomm.support-assistant.anthropic-base-url", MESSAGES_API::baseUrl);
    registry.add("ecomm.support-assistant.storefront-url", () -> "http://localhost:8080");
  }

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @BeforeEach
  void resetMessagesApi() {
    MESSAGES_API.resetAll();
  }

  /** The Messages API answers each call with the next of {@code answers}, in order. */
  static void script(String... answers) {
    for (var i = 0; i < answers.length; i++) {
      MESSAGES_API.stubFor(
          post(urlPathEqualTo("/v1/messages"))
              .inScenario("answers")
              .whenScenarioStateIs(i == 0 ? Scenario.STARTED : "answer " + i)
              .willSetStateTo("answer " + (i + 1))
              .willReturn(okJson(answers[i])));
    }
  }

  /** An answer of Claude's: its content blocks, why it stopped, and the tokens it took. */
  static String answer(String stopReason, String content, String usage) {
    return """
        {"id": "msg_01", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
         "content": [%s], "stop_reason": "%s", "stop_sequence": null, "usage": %s}
        """
        .formatted(content, stopReason, usage);
  }

  static String answer(String stopReason, String content) {
    return answer(
        stopReason,
        content,
        """
        {"input_tokens": 40, "output_tokens": 10, "cache_read_input_tokens": 0,
         "cache_creation_input_tokens": 0}
        """);
  }

  static final String THINKING =
      """
      {"type": "thinking", "thinking": "", "signature": "sig-1"}
      """;

  static String toolUse(String id, String name, String input) {
    return """
        {"type": "tool_use", "id": "%s", "name": "%s", "input": %s}
        """
        .formatted(id, name, input);
  }

  static String text(String text) {
    return """
        {"type": "text", "text": %s}
        """
        .formatted(jsonString(text));
  }

  /** The bodies of the requests the service sent the Messages API, oldest first. */
  static List<JsonNode> requests() {
    return MESSAGES_API.findAll(postRequestedFor(urlPathEqualTo("/v1/messages"))).stream()
        .map(r -> JSON.readTree(r.getBodyAsString()))
        .toList();
  }
}
