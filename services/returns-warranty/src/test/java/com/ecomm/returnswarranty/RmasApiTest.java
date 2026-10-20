package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Base for the RMA tests: delivered Shipments, their Orders and Products as their contexts publish
 * them; the Customer's requests, Staff's steps and the returns Saga's as a client makes them; and
 * the parts of an RMA a client reads.
 */
abstract class RmasApiTest extends ReturnsWarrantyApiTest {

  /** Delivered three days before the fixed clock: within the 14-day Return Period. */
  static final Instant RECENTLY = TestTimeSource.NOW.minus(Duration.ofDays(3));

  /** A valid IMEI, its check digit computed, distinct on every call. */
  static String newImei() {
    var body =
        String.format(
            "%014d",
            Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000_000_000L));
    var sum = 0;
    for (var i = 0; i < 14; i++) {
      var digit = body.charAt(13 - i) - '0';
      if (i % 2 == 0) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
    }
    return body + (10 - sum % 10) % 10;
  }

  static String newVariant(String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }

  /** The JSON of a request, with Serial numbers. */
  static String rma(
      String orderId, String variantId, int quantity, String kind, String... serialNumbers) {
    return """
        {"orderId": "%s", "variantId": "%s", "quantity": %d, "serialNumbers": [%s],
         "kind": "%s", "reason": "It stopped charging"}
        """
        .formatted(
            orderId,
            variantId,
            quantity,
            List.of(serialNumbers).stream()
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(", ")),
            kind);
  }

  RestTestClient.ResponseSpec request(String token, String body) {
    return requestWithKey(token, UUID.randomUUID().toString(), body);
  }

  RestTestClient.ResponseSpec requestWithKey(String token, String key, String body) {
    return http.post()
        .uri("/rmas")
        .headers(h -> h.setBearerAuth(token))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /**
   * The Customer's request once the projections have caught up: until the Shipment is projected it
   * is a 404, and until a warranty claim's Product is, a 503. Neither is recorded under the key, so
   * this asks again with it, as a client would.
   */
  EntityExchangeResult<byte[]> requestedOnceProjected(String body) {
    var key = UUID.randomUUID().toString();
    return await()
        .atMost(Duration.ofSeconds(20))
        .until(
            () -> requestWithKey(CUSTOMER, key, body).expectBody().returnResult(),
            r -> r.getStatus().value() != 404 && r.getStatus().value() != 503);
  }

  /** The RMA the Customer's request created, once the projections have caught up. */
  RmaView requested(String body) {
    var result = requestedOnceProjected(body);
    assertThat(result.getStatus().value())
        .as(new String(result.getResponseBodyContent()))
        .isEqualTo(201);
    return JSON.readValue(result.getResponseBodyContent(), RmaView.class);
  }

  /** The problem detail the Customer's request was refused with, once the projections caught up. */
  tools.jackson.databind.JsonNode refused(String body) {
    var result = requestedOnceProjected(body);
    assertThat(result.getStatus().value())
        .as(new String(result.getResponseBodyContent()))
        .isEqualTo(422);
    return JSON.readTree(result.getResponseBodyContent());
  }

  /**
   * Waits until the Shipment projection has applied everything published before this call: it
   * publishes a delivered Shipment of its own after them and waits until it can be claimed.
   */
  void awaitShipmentsProjected() {
    var marker = newVariant("MARKER");
    var shipment = Shipments.shipment(CUSTOMER_ID).line(marker, "Marker", 1, "NONE");
    shipment.delivered(RECENTLY);
    requested(rma(shipment.orderId(), marker, 1, "RETURN"));
  }

  /**
   * Waits until the Product projection has applied everything published before this call: it
   * publishes a Product with Warranty terms of its own after them, and waits until a warranty claim
   * on it is accepted.
   */
  void awaitProductsProjected() {
    var marker = newVariant("MARKER");
    Products.product("Marker", marker).warranty(12, "Marker").publish();
    var shipment = Shipments.shipment(CUSTOMER_ID).line(marker, "Marker", 1, "NONE");
    shipment.delivered(RECENTLY);
    requested(rma(shipment.orderId(), marker, 1, "WARRANTY_CLAIM"));
  }

  RestTestClient.ResponseSpec approve(String token, String id, String body) {
    return http.post()
        .uri("/rmas/{id}/approve", id)
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  RestTestClient.ResponseSpec reject(String token, String id, String body) {
    return http.post()
        .uri("/rmas/{id}/reject", id)
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** A step after the RMA decision, with a new {@code Idempotency-Key}. */
  RestTestClient.ResponseSpec step(String token, String id, String step, String body) {
    return stepWithKey(token, UUID.randomUUID().toString(), id, step, body);
  }

  RestTestClient.ResponseSpec stepWithKey(
      String token, String key, String id, String step, String body) {
    return http.post()
        .uri("/rmas/{id}/{step}", id, step)
        .headers(h -> h.setBearerAuth(token))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** The RMA, approved by Staff. */
  RmaView approved(RmaView rma) {
    return body(approve(STAFF, rma.id(), "{}").expectStatus().isOk());
  }

  /** The approved RMA, received by Staff with its own units. */
  RmaView received(RmaView rma) {
    var units =
        rma.serialNumbers().isEmpty()
            ? "{\"quantity\": %d}".formatted(rma.quantity())
            : "{\"serialNumbers\": [%s]}"
                .formatted(
                    rma.serialNumbers().stream()
                        .map(s -> "\"" + s + "\"")
                        .collect(Collectors.joining(", ")));
    return body(step(STAFF, rma.id(), "receive", units).expectStatus().isOk());
  }

  /** The received RMA, inspected by Staff with the outcome and a note. */
  RmaView inspected(RmaView rma, String outcome) {
    return body(
        step(
                STAFF,
                rma.id(),
                "inspect",
                "{\"outcome\": \"%s\", \"note\": \"Checked\"}".formatted(outcome))
            .expectStatus()
            .isOk());
  }

  RestTestClient.ResponseSpec read(String token, String id) {
    return http.get().uri("/rmas/{id}", id).headers(h -> h.setBearerAuth(token)).exchange();
  }

  RmaView readRma(String token, String id) {
    return read(token, id)
        .expectStatus()
        .isOk()
        .expectBody(RmaView.class)
        .returnResult()
        .getResponseBody();
  }

  static RmaView body(RestTestClient.ResponseSpec response) {
    return response.expectBody(RmaView.class).returnResult().getResponseBody();
  }

  private static final tools.jackson.databind.json.JsonMapper JSON =
      new tools.jackson.databind.json.JsonMapper();

  /** The parts of an RMA a client reads, independent of the service's classes. */
  record RmaView(
      String id,
      String reference,
      String orderId,
      String customerId,
      String variantId,
      String productName,
      int quantity,
      List<String> serialNumbers,
      String kind,
      String reason,
      WindowView window,
      MoneyView refundAmount,
      String status,
      String requestedAt,
      String sendBackBy,
      String warehouseId,
      Map<String, Object> inspection,
      Map<String, Object> resolution,
      List<HistoryView> history) {}

  record WindowView(String kind, String startsAt, String endsAt, Map<String, Object> warranty) {}

  record MoneyView(long amountMinor, String currency) {}

  record HistoryView(String status, String by, String at, String note) {}

  record PageView(List<RmaView> items, int page, int size, long total) {}
}
