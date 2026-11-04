package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Asked about an Order by its Order reference, with or without its {@code #}, the mock provider
 * finds the Customer's Orders with that reference, from Order Management with their own token. One
 * is answered; several have it ask which one; none is answered as not found among theirs, which is
 * how another Customer's Order with that reference answers too.
 */
class OrderByReferenceApiTest extends SupportAssistantApiTest {

  @Test
  void anOrderIsFoundByItsReferenceWithTheCustomersOwnToken() {
    var paid = order(ORDER_ID, "PAID", "PLACED", "PAID");
    stubOrdersByReference("7F1C2A3B", paid);
    stubOrder(ORDER_ID, okJson(paid));
    var id = started();

    send(id, "What's the status of order #7f1c2a3b?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Your Order #7F1C2A3B is paid. It was placed on 3 November 2026 and paid on 3 November"
                + " 2026.")
        .jsonPath("$.references[0].id")
        .isEqualTo(ORDER_ID);

    ORDER_MANAGEMENT.verify(
        getRequestedFor(urlPathEqualTo("/orders"))
            .withQueryParam("reference", equalTo("7F1C2A3B"))
            .withHeader("Authorization", equalTo("Bearer " + customerToken()))
            .withHeader("X-Correlation-Id", equalTo("test-correlation-1")));
  }

  @Test
  void aReferenceWithoutItsHashFindsItToo() {
    var paid = order(ORDER_ID, "PAID", "PLACED", "PAID");
    stubOrdersByReference("7F1C2A3B", paid);
    stubOrder(ORDER_ID, okJson(paid));
    var id = started();

    send(id, "Status of 7F1C2A3B please")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(String.class, text -> assertThat(text).startsWith("Your Order #7F1C2A3B is paid."));
  }

  @Test
  void aReferenceNoneOfTheCustomersOrdersHasIsNotFoundAmongTheirs() {
    stubOrdersByReference("DEADBEEF");
    var id = started();

    send(id, "Where is my order #DEADBEEF?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "I couldn't find an Order #DEADBEEF among yours. Please check the reference in your"
                + " email.")
        .jsonPath("$.references")
        .isEmpty();
  }

  @Test
  void severalOrdersWithTheReferenceHaveItAskWhichOne() {
    stubOrdersByReference(
        "7F1C2A3B",
        order(ORDER_ID, "PAID", "PAID"),
        order("7f1c2a3b-0000-4000-8000-000000000009", "DELIVERED", "DELIVERED"));
    var id = started();

    send(id, "Where is order #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "2 of your Orders have the reference #7F1C2A3B: one paid, placed on 3 November 2026"
                + " for 803.99 EUR, and one delivered, placed on 3 November 2026 for 803.99 EUR."
                + " Which one do you mean?")
        .jsonPath("$.references.length()")
        .isEqualTo(2);
  }

  @Test
  void anRmaReferenceIsntTakenForAnOrderReference() {
    stubLatestOrderPaid();
    var id = started();

    send(id, "What's the status of my order for RMA-3F2A9C1B?").expectStatus().isOk();

    ORDER_MANAGEMENT.verify(
        0,
        getRequestedFor(urlPathEqualTo("/orders"))
            .withQueryParam("reference", equalTo("3F2A9C1B")));
  }
}
