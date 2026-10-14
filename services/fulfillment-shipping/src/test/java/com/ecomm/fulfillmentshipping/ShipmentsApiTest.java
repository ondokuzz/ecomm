package com.ecomm.fulfillmentshipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Base for the Shipment tests: Orchestration's commands, Staff's, and the reads, as a client makes
 * them, and the parts of a Shipment a client reads.
 */
abstract class ShipmentsApiTest extends FulfillmentShippingApiTest {

  /** A phone tracked by IMEI, a laptop by serial number, and a case not tracked, all projected. */
  static final String PHONE = "PHN-" + UUID.randomUUID();

  static final String LAPTOP = "LAP-" + UUID.randomUUID();
  static final String CASE = "CASE-" + UUID.randomUUID();

  static {
    Products.product("Pixel 9", PHONE).tracked("IMEI").publish();
    Products.product("ThinkPad X1", LAPTOP).tracked("SERIAL").publish();
    Products.product("Pixel 9 Case", CASE).publish();
  }

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

  static String newSerial() {
    return "sn-" + UUID.randomUUID().toString().substring(0, 13);
  }

  static String address(String country) {
    return """
        {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5", "postcode": "10115",
         "city": "Berlin", "country": "%s"}
        """
        .formatted(country);
  }

  static final String EXPRESS =
      """
      {"id": "EXPRESS", "carrier": "Mock Carrier", "cost": {"amountMinor": 999, "currency": "EUR"},
       "estimatedDays": 1}
      """;

  /** The JSON of lines, from Variant ID to quantity, in order. */
  static String lines(Map<String, Integer> quantities) {
    return quantities.entrySet().stream()
        .map(e -> "{\"variantId\": \"%s\", \"quantity\": %d}".formatted(e.getKey(), e.getValue()))
        .collect(Collectors.joining(", ", "[", "]"));
  }

  static String shipmentRequest(String orderId, String customerId, String country, String lines) {
    return """
        {"orderId": "%s", "customerId": "%s", "lines": %s, "shippingAddress": %s,
         "shippingOption": %s}
        """
        .formatted(orderId, customerId, lines, address(country), EXPRESS);
  }

  /** Creates the Shipment as the fulfillment Saga does, with a fresh key. */
  RestTestClient.ResponseSpec create(String body) {
    return createWithKey(UUID.randomUUID().toString(), body);
  }

  RestTestClient.ResponseSpec createWithKey(String key, String body) {
    return http.post()
        .uri("/shipments")
        .headers(h -> h.setBearerAuth(ORCHESTRATION))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** A new Shipment of a phone and two cases for {@code customer-42}, to Germany. */
  ShipmentView created() {
    return created("DE", Map.of(PHONE, 1, CASE, 2));
  }

  /** A new Shipment for a new Order of {@code customer-42}. */
  ShipmentView created(String country, Map<String, Integer> quantities) {
    var body =
        shipmentRequest(UUID.randomUUID().toString(), "customer-42", country, lines(quantities));
    return createdFrom(body);
  }

  /**
   * Creates the Shipment, waiting for the Product projection to know its Variants: until then it is
   * a 503, which the fulfillment Saga retries, as this does with the same key.
   */
  ShipmentView createdFrom(String body) {
    var key = UUID.randomUUID().toString();
    var result =
        await()
            .atMost(Duration.ofSeconds(20))
            .until(
                () -> createWithKey(key, body).returnResult(ShipmentView.class),
                r -> r.getStatus().value() != 503);
    assertThat(result.getStatus().value()).isEqualTo(201);
    return result.getResponseBody();
  }

  RestTestClient.ResponseSpec pick(String id) {
    return http.post()
        .uri("/shipments/{id}/pick", id)
        .headers(h -> h.setBearerAuth(STAFF))
        .exchange();
  }

  RestTestClient.ResponseSpec pack(String id, String body) {
    return http.post()
        .uri("/shipments/{id}/pack", id)
        .headers(h -> h.setBearerAuth(STAFF))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** The pack request for the Serial numbers, by Variant ID. */
  static String serials(Map<String, List<String>> serialNumbers) {
    return serialNumbers.entrySet().stream()
        .map(
            e ->
                "\"%s\": [%s]"
                    .formatted(
                        e.getKey(),
                        e.getValue().stream()
                            .map(s -> "\"" + s + "\"")
                            .collect(Collectors.joining(", "))))
        .collect(Collectors.joining(", ", "{\"serialNumbers\": {", "}}"));
  }

  RestTestClient.ResponseSpec dispatch(String id) {
    return http.post()
        .uri("/shipments/{id}/dispatch", id)
        .headers(h -> h.setBearerAuth(ORCHESTRATION))
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .exchange();
  }

  RestTestClient.ResponseSpec hold(String id, String reason) {
    return http.post()
        .uri("/shipments/{id}/hold", id)
        .headers(h -> h.setBearerAuth(ORCHESTRATION))
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"reason\": \"%s\"}".formatted(reason))
        .exchange();
  }

  /** A Shipment picked and packed by Staff, its phone's IMEI entered. */
  ShipmentView packed() {
    var shipment = created();
    pick(shipment.id()).expectStatus().isOk();
    return pack(shipment.id(), serials(Map.of(PHONE, List.of(newImei()))))
        .expectStatus()
        .isOk()
        .expectBody(ShipmentView.class)
        .returnResult()
        .getResponseBody();
  }

  /** A packed Shipment, then booked with the Carrier. */
  ShipmentView shipped() {
    return dispatch(packed().id())
        .expectStatus()
        .isOk()
        .expectBody(ShipmentView.class)
        .returnResult()
        .getResponseBody();
  }

  ShipmentView read(String token, String id) {
    return http.get()
        .uri("/shipments/{id}", id)
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ShipmentView.class)
        .returnResult()
        .getResponseBody();
  }

  /** Waits for a condition on the Shipment as Staff read it. */
  ShipmentView awaitShipment(String id, java.util.function.Predicate<ShipmentView> condition) {
    return await().atMost(Duration.ofSeconds(10)).until(() -> read(STAFF, id), condition::test);
  }

  /** The parts of a Shipment a client reads, independent of the service's classes. */
  record ShipmentView(
      String id,
      String orderId,
      String customerId,
      String warehouseId,
      String status,
      Map<String, String> shippingAddress,
      OptionView shippingOption,
      List<LineView> lines,
      String carrier,
      String trackingNumber,
      List<TrackingEventView> trackingEvents,
      String deliveredAt,
      String holdReason,
      List<HistoryView> statusHistory,
      String createdAt) {}

  record OptionView(String id, String carrier, MoneyView cost, Integer estimatedDays) {}

  record MoneyView(long amountMinor, String currency) {}

  record LineView(
      String variantId,
      String productName,
      int quantity,
      String serialTracking,
      List<String> serialNumbers) {}

  record TrackingEventView(String eventId, String status, String occurredAt) {}

  record HistoryView(String status, String at, String by) {}
}
