package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Placing an Order and each Status change publish one {@code order-management.order} event: keyed
 * by the Order's ID, valid against its schema, carrying the request's Correlation ID and a higher
 * version than the last. A refused change publishes nothing.
 */
class OrderEventsApiTest extends OrderApiTest {

  @Test
  void placingAnOrderPublishesItsSnapshot() {
    var order =
        http.post()
            .uri("/orders")
            .headers(h -> h.setBearerAuth(orchestrationToken()))
            .header("Idempotency-Key", newKey())
            .header("X-Correlation-Id", "order-events-place-1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {"customerId": "customer-events", "lines": [
                  {"variantId": "PHN-PIXEL-9", "quantity": 2,
                   "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}
                ],
                 "discounts": [
                   {"source": "CAMPAIGN", "campaignId": "7f1c2b9e-0000-4000-8000-000000000001",
                    "campaignName": "Phone week", "amount": {"amountMinor": 7990, "currency": "EUR"}},
                   {"source": "COUPON", "couponCode": "WELCOME10",
                    "amount": {"amountMinor": 7990, "currency": "EUR"}}],
                 "tax": {"amountMinor": 28764, "currency": "EUR"}}
                """)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(OrderView.class)
            .returnResult()
            .getResponseBody();

    var records = OrderEvents.keyed(order.id(), 1);

    assertThat(records).hasSize(1);
    var record = records.getFirst();
    assertThat(OrderEvents.schemaViolationsOf(record)).isEmpty();
    var header = record.headers().lastHeader("X-Correlation-Id");
    assertThat(new String(header.value(), StandardCharsets.UTF_8))
        .isEqualTo("order-events-place-1");
    var event = OrderEvents.valueOf(record);
    assertThat(event.get("orderId").asText()).isEqualTo(order.id());
    assertThat(event.get("change").asText()).isEqualTo("PLACED");
    assertThat(event.get("version").asLong()).isEqualTo(1);
    var snapshot = event.get("order");
    assertThat(snapshot.get("customerId").asText()).isEqualTo("customer-events");
    assertThat(snapshot.get("status").asText()).isEqualTo("PLACED");
    assertThat(Instant.parse(snapshot.get("placedAt").asText()))
        .isEqualTo(Instant.parse(order.placedAt()));
    assertThat(snapshot.at("/lines/0/variantId").asText()).isEqualTo("PHN-PIXEL-9");
    assertThat(snapshot.at("/lines/0/quantity").asInt()).isEqualTo(2);
    assertThat(snapshot.at("/lines/0/unitPrice/amountMinor").asLong()).isEqualTo(79900);
    assertThat(snapshot.at("/discounts/0/source").asText()).isEqualTo("CAMPAIGN");
    assertThat(snapshot.at("/discounts/0/campaignId").asText())
        .isEqualTo("7f1c2b9e-0000-4000-8000-000000000001");
    assertThat(snapshot.at("/discounts/0/campaignName").asText()).isEqualTo("Phone week");
    assertThat(snapshot.at("/discounts/0/amount/amountMinor").asLong()).isEqualTo(7990);
    assertThat(snapshot.at("/discounts/0/couponCode").isMissingNode()).isTrue();
    assertThat(snapshot.at("/discounts/1/source").asText()).isEqualTo("COUPON");
    assertThat(snapshot.at("/discounts/1/couponCode").asText()).isEqualTo("WELCOME10");
    assertThat(snapshot.at("/discounts/1/amount/amountMinor").asLong()).isEqualTo(7990);
    assertThat(snapshot.at("/discounts/1/campaignId").isMissingNode()).isTrue();
    assertThat(snapshot.at("/tax/amountMinor").asLong()).isEqualTo(28764);
    assertThat(snapshot.at("/subtotal/amountMinor").asLong()).isEqualTo(159800);
    assertThat(snapshot.at("/total/amountMinor").asLong()).isEqualTo(172584);
    assertThat(snapshot.at("/total/currency").asText()).isEqualTo("EUR");
    assertThat(snapshot.get("statusHistory")).hasSize(1);
    assertThat(snapshot.at("/statusHistory/0/status").asText()).isEqualTo("PLACED");
    assertThat(snapshot.at("/statusHistory/0/changedBy").asText()).isEqualTo("ORCHESTRATION");
  }

  @Test
  void anOrderWithoutADiscountPublishesNoDiscounts() {
    var order = placed("customer-events");

    var event = OrderEvents.valueOf(OrderEvents.keyed(order.id(), 1).getFirst());

    assertThat(event.at("/order/discounts")).isEmpty();
  }

  @Test
  void eachStatusChangePublishesOneEventWithAHigherVersion() {
    var id = placed("customer-events").id();
    changeStatusWithCorrelation(id, "PAID", "order-events-pay-1").expectStatus().isOk();
    changeStatusWithCorrelation(id, "CANCELLED", "order-events-cancel-1").expectStatus().isOk();

    var records = OrderEvents.keyed(id, 3);

    assertThat(records).hasSize(3);
    assertThat(records).allSatisfy(r -> assertThat(OrderEvents.schemaViolationsOf(r)).isEmpty());
    var events = records.stream().map(OrderEvents::valueOf).toList();
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("PLACED", "STATUS_CHANGED", "STATUS_CHANGED");
    assertThat(events).extracting(e -> e.get("version").asLong()).containsExactly(1L, 2L, 3L);
    assertThat(events)
        .extracting(e -> e.at("/order/status").asText())
        .containsExactly("PLACED", "PAID", "CANCELLED");
    var cancelled = events.getLast().at("/order/statusHistory");
    assertThat(cancelled)
        .extracting(e -> e.get("status").asText())
        .containsExactly("PLACED", "PAID", "CANCELLED");
    assertThat(records)
        .extracting(r -> new String(r.headers().lastHeader("X-Correlation-Id").value()))
        .endsWith("order-events-pay-1", "order-events-cancel-1");
  }

  @Test
  void aRefusedStatusChangePublishesNothing() {
    var id = placed().id();

    changeStatus(id, "SHIPPED").expectStatus().isEqualTo(409);
    changeStatus(id, "PAID").expectStatus().isOk();

    var events = OrderEvents.keyed(id, 2).stream().map(OrderEvents::valueOf).toList();
    assertThat(events)
        .extracting(e -> e.at("/order/status").asText())
        .containsExactly("PLACED", "PAID");
  }

  private RestTestClient.ResponseSpec changeStatusWithCorrelation(
      String id, String status, String correlationId) {
    return http.patch()
        .uri("/orders/{id}/status", id)
        .headers(h -> h.setBearerAuth(orchestrationToken()))
        .header("Idempotency-Key", newKey())
        .header("X-Correlation-Id", correlationId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(statusChange("customer-events", status))
        .exchange();
  }
}
