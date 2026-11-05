package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.domain.Entry;
import com.ecomm.supportassistant.domain.ToolCall;
import com.ecomm.supportassistant.domain.ToolResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * What the provider is handed and how its reply is grounded, with a scripted provider in place of
 * the mock: the references are the records the turn's lookups returned that the reply names, never
 * what the model's text makes up; the system prompt is the same fixed text on every call; and the
 * Conversation the provider reads keeps the earlier turns' lookups.
 */
@Import(GroundingApiTest.Provider.class)
class GroundingApiTest extends SupportAssistantApiTest {

  @TestConfiguration
  static class Provider {

    @Bean
    @Primary
    ScriptedSupportModel scriptedSupportModel() {
      return new ScriptedSupportModel();
    }
  }

  @Autowired ScriptedSupportModel model;

  @BeforeEach
  void resetModel() {
    model.reset();
  }

  static SupportModel.ToolUse findOrders() {
    return new SupportModel.ToolUse(
        List.of(new ToolCall("call-1", "find_orders", Map.of())), SupportModel.Usage.NONE);
  }

  static SupportModel.Reply reply(String text) {
    return new SupportModel.Reply(text, SupportModel.Usage.NONE);
  }

  @Test
  void onlyRecordsTheLookupsReturnedAndTheReplyNamesAreReferenced() {
    stubOrders(order(ORDER_ID, "PAID", "PAID"), order(OTHER_ORDER_ID, "DELIVERED", "DELIVERED"));
    model.script(findOrders(), reply("Your Order #7F1C2A3B is paid, and so is #DEADBEEF."));
    var id = started();

    send(id, "Where are my orders?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.references.length()")
        .isEqualTo(1)
        .jsonPath("$.references[0].id")
        .isEqualTo(ORDER_ID);
  }

  @Test
  void aReferenceIsNamedInEitherCaseWithOrWithoutItsHash() {
    stubOrders(order(ORDER_ID, "PAID", "PAID"), order(OTHER_ORDER_ID, "DELIVERED", "DELIVERED"));
    model.script(findOrders(), reply("Order 7f1c2a3b is paid; order #e5d40c11 was delivered."));
    var id = started();

    send(id, "Where are my orders?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.references[*].id")
        .isEqualTo(List.of(ORDER_ID, OTHER_ORDER_ID));
  }

  static SupportModel.ToolUse searchProducts(String query) {
    return new SupportModel.ToolUse(
        List.of(new ToolCall("call-1", "search_products", Map.of("query", query))),
        SupportModel.Usage.NONE);
  }

  @Test
  void aProductIsNamedByItsNameOrItsSku() {
    stubSearch(
        "phones",
        productSummary("PHN-PIXEL-9", "Google Pixel 9", 79900, true, true),
        productSummary("PHN-GALAXY-S24", "Samsung Galaxy S24", 89900, false, true),
        productSummary("PHN-NOTHING-2A", "Nothing Phone (2a)", 34900, false, true));
    model.script(
        searchProducts("phones"),
        reply("The google pixel 9 is from 799.00 EUR, and PHN-GALAXY-S24 is 899.00 EUR."));
    var id = started();

    send(id, "Which phones do you have?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.references[*].id")
        .isEqualTo(List.of("PHN-PIXEL-9", "PHN-GALAXY-S24"))
        .jsonPath("$.references[0].kind")
        .isEqualTo("PRODUCT")
        .jsonPath("$.references[0].reference")
        .isEqualTo("PHN-PIXEL-9")
        .jsonPath("$.references[0].label")
        .isEqualTo("Google Pixel 9");
  }

  @Test
  void aProductIsntNamedByTheStartOfAnothersName() {
    stubSearch(
        "pixel",
        productSummary("PHN-PIXEL-9", "Google Pixel 9", 79900, true, true),
        productSummary("PHN-PIXEL-9-PRO", "Google Pixel 9 Pro", 109900, false, true));
    model.script(searchProducts("pixel"), reply("The Google Pixel 9 Pro is 1099.00 EUR."));
    var id = started();

    send(id, "Is the pixel pro in stock?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.references[*].id")
        .isEqualTo(List.of("PHN-PIXEL-9-PRO"));
  }

  @Test
  void aReferenceIsntNamedByAPartOfALongerWord() {
    stubOrders(order(ORDER_ID, "PAID", "PAID"));
    model.script(findOrders(), reply("Your Order 7F1C2A3B0 is paid."));
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.references")
        .isEmpty();
  }

  @Test
  void theLookupsResultReachesTheNextProviderCallTrimmed() {
    stubOrders(order(ORDER_ID, "PAID", "PLACED", "PAID"));
    model.script(findOrders(), reply("Your Order #7F1C2A3B is paid."));
    var id = started();

    send(id, "Where is my order?").expectStatus().isOk();

    var second = model.requests().get(1).transcript();
    var results = (Entry.ToolResults) second.getLast();
    var result = results.results().getFirst();
    assertThat(result.callId()).isEqualTo("call-1");
    assertThat(result.outcome()).isEqualTo(ToolResult.Outcome.FOUND);
    assertThat(result.content())
        .isEqualTo(
            Map.of(
                "orders",
                List.of(
                    Map.of(
                        "orderId", ORDER_ID,
                        "reference", "#7F1C2A3B",
                        "status", "PAID",
                        "placedAt", "2026-11-03T10:15:00Z",
                        "total", Map.of("amount", "803.99", "currency", "EUR")))));
  }

  @Test
  void theSystemPromptIsTheSameFixedTextOnEveryCall() {
    stubOrders(order(ORDER_ID, "PAID", "PAID"));
    model.script(findOrders(), reply("Your Order #7F1C2A3B is paid."), reply("You're welcome."));
    var id = started();

    send(id, "Where is my order?").expectStatus().isOk();
    send(id, "Thanks!").expectStatus().isOk();

    var prompts = model.requests().stream().map(SupportModel.Request::systemPrompt).distinct();
    assertThat(prompts)
        .singleElement()
        .satisfies(
            prompt -> {
              assertThat(prompt).doesNotContain(CUSTOMER_ID);
              assertThat(prompt).doesNotContainPattern("\\d{4}-\\d{2}-\\d{2}|20\\d\\d");
            });
    assertThat(model.requests().getFirst().tools())
        .extracting(SupportModel.ToolDefinition::name)
        .containsExactly(
            "find_orders",
            "get_order",
            "get_shipment",
            "get_payments",
            "get_payment_transaction_history",
            "find_rmas",
            "get_rma",
            "check_return_eligibility",
            "search_products",
            "get_product");
  }

  @Test
  void aFollowUpReadsTheEarlierTurnsLookups() {
    stubOrders(order(ORDER_ID, "PAID", "PAID"));
    model.script(findOrders(), reply("Your Order #7F1C2A3B is paid."), reply("It's paid."));
    var id = started();
    send(id, "Where is my order?").expectStatus().isOk();

    send(id, "And is it paid?").expectStatus().isOk();

    var transcript = model.requests().getLast().transcript();
    assertThat(transcript)
        .extracting(entry -> entry.getClass().getSimpleName())
        .containsExactly(
            "CustomerMessage", "ToolUse", "ToolResults", "AssistantReply", "CustomerMessage");
    assertThat(((Entry.ToolResults) transcript.get(2)).results().getFirst().records())
        .extracting(r -> r.id())
        .containsExactly(ORDER_ID);
  }

  @Test
  void getOrderWithoutAnOrderIdIsAnErrorResultAndAsksNothing() {
    model.script(
        new SupportModel.ToolUse(
            List.of(new ToolCall("call-1", "get_order", Map.of())), SupportModel.Usage.NONE),
        reply("I couldn't look that up."));
    var id = started();

    send(id, "Where is my order?").expectStatus().isOk();

    var results = (Entry.ToolResults) model.requests().get(1).transcript().getLast();
    assertThat(results.results().getFirst().outcome()).isEqualTo(ToolResult.Outcome.ERROR);
    ORDER_MANAGEMENT.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void anAnswerOrderManagementCantHaveGivenIsAnErrorResult() {
    stubOrders(okJson("{\"items\": [{\"id\": \"" + ORDER_ID + "\"}]}"));
    model.script(findOrders(), reply("I couldn't look that up."));
    var id = started();

    send(id, "Where is my order?").expectStatus().isOk();

    var results = (Entry.ToolResults) model.requests().get(1).transcript().getLast();
    assertThat(results.results().getFirst().outcome()).isEqualTo(ToolResult.Outcome.ERROR);
  }
}
