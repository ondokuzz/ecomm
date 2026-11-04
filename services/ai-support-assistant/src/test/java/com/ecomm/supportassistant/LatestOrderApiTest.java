package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.Test;

/**
 * Asked where their Order is, the mock provider finds the Customer's latest Order and looks it up
 * with its Order Status history, from Order Management with the Customer's own token, and the reply
 * names it with a link. A lookup that finds nothing or fails is said so, and the turn still
 * answers.
 */
class LatestOrderApiTest extends SupportAssistantApiTest {

  @Test
  void theReplyGivesTheLatestOrdersStatusAndLinksIt() {
    stubLatestOrderPaid();
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.role")
        .isEqualTo("ASSISTANT")
        .jsonPath("$.text")
        .isEqualTo(
            "Your latest Order, #7F1C2A3B, is paid. It was placed on 3 November 2026 and paid"
                + " on 3 November 2026.")
        .jsonPath("$.references.length()")
        .isEqualTo(1)
        .jsonPath("$.references[0].kind")
        .isEqualTo("ORDER")
        .jsonPath("$.references[0].id")
        .isEqualTo(ORDER_ID)
        .jsonPath("$.references[0].reference")
        .isEqualTo("#7F1C2A3B")
        .jsonPath("$.references[0].label")
        .isEqualTo("Order #7F1C2A3B")
        .jsonPath("$.at")
        .isNotEmpty();
  }

  @Test
  void orderManagementIsReadWithTheCustomersOwnTokenAndTheCorrelationId() {
    stubLatestOrderPaid();
    var id = started();

    send(id, "Where is my order?").expectStatus().isOk();

    var bearer = equalTo("Bearer " + customerToken());
    var correlation = equalTo("test-correlation-1");
    ORDER_MANAGEMENT.verify(
        getRequestedFor(urlPathEqualTo("/orders"))
            .withQueryParam("page", equalTo("0"))
            .withHeader("Authorization", bearer)
            .withHeader("X-Correlation-Id", correlation));
    ORDER_MANAGEMENT.verify(
        getRequestedFor(urlPathEqualTo("/orders/" + ORDER_ID))
            .withHeader("Authorization", bearer)
            .withHeader("X-Correlation-Id", correlation));
  }

  @Test
  void aCustomerWithoutOrdersIsToldSo() {
    stubOrders();
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("You haven't placed any Orders yet.")
        .jsonPath("$.references")
        .isEmpty();
  }

  @Test
  void anOrderOrderManagementDoesntFindIsNotFoundNotAFailure() {
    stubOrders(order(ORDER_ID, "PAID", "PLACED", "PAID"));
    stubOrder(ORDER_ID, notFound());
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("I couldn't find that Order among yours.")
        .jsonPath("$.references")
        .isEmpty();
  }

  @Test
  void orderManagementFailingIsSaidSoAndTheTurnStillAnswers() {
    stubOrders(serviceUnavailable());
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(
            String.class,
            text -> assertThat(text).contains("couldn't look up your Orders just now"));
  }

  @Test
  void orderManagementTimingOutIsSaidSo() {
    stubOrders(
        okJson("{\"items\": [], \"page\": 0, \"size\": 5, \"total\": 0}").withFixedDelay(3_000));
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(
            String.class,
            text -> assertThat(text).contains("couldn't look up your Orders just now"));
  }

  @Test
  void aDroppedConnectionToOrderManagementIsSaidSo() {
    stubOrders(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(
            String.class,
            text -> assertThat(text).contains("couldn't look up your Orders just now"));
  }

  @Test
  void aQuestionAboutSomethingElseLooksNothingUp() {
    var id = started();

    send(id, "Hello!")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(String.class, text -> assertThat(text).contains("I can help with your Orders"));

    ORDER_MANAGEMENT.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void theTranscriptHoldsTheMessageAndTheReplyWithItsReferences() {
    stubLatestOrderPaid();
    var id = started();
    send(id, "Where is my order?").expectStatus().isOk();

    transcript(customerToken(), id)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.messages.length()")
        .isEqualTo(2)
        .jsonPath("$.messages[0].role")
        .isEqualTo("CUSTOMER")
        .jsonPath("$.messages[0].text")
        .isEqualTo("Where is my order?")
        .jsonPath("$.messages[0].references")
        .isEmpty()
        .jsonPath("$.messages[1].role")
        .isEqualTo("ASSISTANT")
        .jsonPath("$.messages[1].text")
        .value(String.class, text -> assertThat(text).contains("#7F1C2A3B"))
        .jsonPath("$.messages[1].references[0].id")
        .isEqualTo(ORDER_ID);
  }
}
