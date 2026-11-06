package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** With an API key, Claude answers, through the Messages API. */
@ExtendWith(OutputCaptureExtension.class)
class ClaudeApiTest extends MessagesApiTest {

  @Test
  void claudeLooksUpWhatItAsksForThenReplies() {
    stubLatestOrderPaid();
    script(
        answer("tool_use", THINKING + "," + toolUse("toolu_01", "find_orders", "{}")),
        answer("end_turn", text("Your latest Order, #7F1C2A3B, is paid.")));
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("Your latest Order, #7F1C2A3B, is paid.")
        .jsonPath("$.references[0].id")
        .isEqualTo(ORDER_ID);

    ORDER_MANAGEMENT.verify(
        getRequestedFor(urlPathEqualTo("/orders"))
            .withHeader("Authorization", equalTo("Bearer " + customerToken())));
    var second = requests().get(1).get("messages");
    assertThat(second.size()).isEqualTo(3);
    // Claude's answer goes back as it gave it, its thinking included.
    assertThat(second.get(1).get("role").asString()).isEqualTo("assistant");
    assertThat(second.get(1).get("content").get(0).get("signature").asString()).isEqualTo("sig-1");
    var result = second.get(2).get("content").get(0);
    assertThat(result.get("type").asString()).isEqualTo("tool_result");
    assertThat(result.get("tool_use_id").asString()).isEqualTo("toolu_01");
    assertThat(result.get("content").toString()).contains("#7F1C2A3B");
  }

  @Test
  void theRequestAsksForOpusAtLowEffortWithStrictToolsAndTheSystemPromptCached() {
    stubLatestOrderPaid();
    script(
        answer("tool_use", toolUse("toolu_01", "find_orders", "{}")),
        answer("end_turn", text("Your latest Order, #7F1C2A3B, is paid.")));
    var id = started();

    send(id, "Where is my order?").expectStatus().isOk();

    MESSAGES_API.verify(
        2,
        postRequestedFor(urlPathEqualTo("/v1/messages"))
            .withHeader("Authorization", equalTo("Bearer " + API_KEY))
            .withHeader("anthropic-beta", containing("server-side-fallback-2026-07-01")));
    var requests = requests();
    var first = requests.get(0);
    assertThat(first.get("model").asString()).isEqualTo("claude-opus-5-5");
    assertThat(first.get("max_tokens").asInt()).isEqualTo(4000);
    assertThat(first.get("output_config").get("effort").asString()).isEqualTo("low");
    // Adaptive thinking, the model's default, is never turned off.
    assertThat(first.has("thinking")).isFalse();
    assertThat(first.has("stream") && first.get("stream").asBoolean()).isFalse();
    assertThat(first.get("tool_choice").get("type").asString()).isEqualTo("auto");
    assertThat(first.get("fallbacks").asString()).isEqualTo("default");
    assertThat(first.get("tools").size()).isEqualTo(10);
    for (var tool : first.get("tools")) {
      assertThat(tool.get("strict").asBoolean()).as(tool.get("name").asString()).isTrue();
      assertThat(tool.get("input_schema").get("additionalProperties").asBoolean()).isFalse();
    }
    // One breakpoint, after the system prompt, which follows the tools: nothing before it changes.
    var system = first.get("system");
    assertThat(system.size()).isEqualTo(1);
    assertThat(system.get(0).get("cache_control").get("type").asString()).isEqualTo("ephemeral");
    assertThat(first.get("tools").toString()).doesNotContain("cache_control");
    assertThat(system.toString())
        .doesNotContain(CUSTOMER_ID)
        .doesNotContain("Where is my order?")
        .doesNotContainPattern("20\\d\\d")
        .doesNotContain("localhost");
    assertThat(requests.get(1).get("system")).isEqualTo(system);
    assertThat(requests.get(1).get("tools")).isEqualTo(first.get("tools"));
    // The service, not the Customer, prefixes the day it was sent and the Storefront's address.
    assertThat(first.get("messages").get(0).get("content").get(0).get("text").asString())
        .matches(
            "\\[Sent on \\w+day \\d{1,2} \\w+ 20\\d\\d\\. The Storefront is at"
                + " http://localhost:8080\\.\\]\n\nWhere is my order\\?");
  }

  @Test
  void theLookupsOfOneAnswerAllGoBackTogether() {
    stubOrder(ORDER_ID, okJson(order(ORDER_ID, "PAID", "PAID")));
    stubOrder(OTHER_ORDER_ID, okJson(order(OTHER_ORDER_ID, "DELIVERED", "DELIVERED")));
    script(
        answer(
            "tool_use",
            toolUse("toolu_01", "get_order", "{\"orderId\": \"%s\"}".formatted(ORDER_ID))
                + ","
                + toolUse(
                    "toolu_02", "get_order", "{\"orderId\": \"%s\"}".formatted(OTHER_ORDER_ID))),
        answer("end_turn", text("#7F1C2A3B is paid and #E5D40C11 delivered.")));
    var id = started();

    send(id, "How are my two orders?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.references[*].id")
        .isEqualTo(List.of(ORDER_ID, OTHER_ORDER_ID));

    for (var orderId : List.of(ORDER_ID, OTHER_ORDER_ID)) {
      ORDER_MANAGEMENT.verify(
          getRequestedFor(urlPathEqualTo("/orders/" + orderId))
              .withHeader("Authorization", equalTo("Bearer " + customerToken()))
              .withHeader("X-Correlation-Id", equalTo("test-correlation-1")));
    }
    var results = requests().get(1).get("messages").get(2).get("content");
    assertThat(results.size()).isEqualTo(2);
    assertThat(results.get(0).get("tool_use_id").asString()).isEqualTo("toolu_01");
    assertThat(results.get(0).get("content").asString()).contains("#7F1C2A3B");
    assertThat(results.get(1).get("tool_use_id").asString()).isEqualTo("toolu_02");
    assertThat(results.get(1).get("content").asString()).contains("#E5D40C11");
  }

  @Test
  void aLookupThatFailsGoesBackAsAnError() {
    stubOrders(serviceUnavailable());
    script(
        answer("tool_use", toolUse("toolu_01", "find_orders", "{}")),
        answer("end_turn", text("I couldn't look up your Orders just now.")));
    var id = started();

    send(id, "Where is my order?").expectStatus().isOk();

    var result = requests().get(1).get("messages").get(2).get("content").get(0);
    assertThat(result.get("is_error").asBoolean()).isTrue();
  }

  @Test
  void aRefusalIsAReplySayingSo() {
    script(answer("refusal", ""));
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(String.class, text -> assertThat(text).contains("can't help with that"))
        .jsonPath("$.references.length()")
        .isEqualTo(0);
  }

  @Test
  void anAnswerCutOffIsAReplySayingSo() {
    script(answer("max_tokens", THINKING + "," + text("Your Orders are")));
    var id = started();

    send(id, "Tell me everything about all my orders.")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(String.class, text -> assertThat(text).contains("too long to finish"));
  }

  @Test
  void anyOtherStopIsAnsweredUnavailable() {
    script(answer("pause_turn", THINKING));
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("assistantUnavailable");
    transcript(customerToken(), id).expectBody().jsonPath("$.messages").isEmpty();
  }

  @Test
  void aLaterTurnHandsBackTheEarlierRepliesAsClaudeGaveThem() {
    script(
        answer("end_turn", THINKING + "," + text("Hello! What can I look up for you?")),
        answer("end_turn", text("Which Order do you mean?")));
    var id = started();
    send(id, "Hi").expectStatus().isOk();

    send(id, "Is it paid?").expectStatus().isOk();

    var messages = requests().get(1).get("messages");
    assertThat(messages.size()).isEqualTo(3);
    assertThat(messages.get(1).get("content").get(0).get("signature").asString())
        .isEqualTo("sig-1");
    assertThat(messages.get(1).get("content").get(1).get("text").asString())
        .isEqualTo("Hello! What can I look up for you?");
  }

  @Test
  void aTurnsTokensAreLogged(CapturedOutput output) {
    stubLatestOrderPaid();
    script(
        answer(
            "tool_use",
            toolUse("toolu_01", "find_orders", "{}"),
            """
            {"input_tokens": 30, "output_tokens": 12, "cache_read_input_tokens": 0,
             "cache_creation_input_tokens": 2900}
            """),
        answer(
            "end_turn",
            text("Your latest Order, #7F1C2A3B, is paid."),
            """
            {"input_tokens": 400, "output_tokens": 20, "cache_read_input_tokens": 2900,
             "cache_creation_input_tokens": 0}
            """));
    var id = started();

    send(id, "Where is my order?").expectStatus().isOk();

    assertThat(output.getOut())
        .contains(
            "Turn in Conversation "
                + id
                + " replied: tools [find_orders found], 2 provider calls, tokens 430 in, 32 out,"
                + " 2900 cache read")
        .doesNotContain(API_KEY);
  }
}
