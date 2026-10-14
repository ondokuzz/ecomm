package com.ecomm.fulfillmentshipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * The fulfillment Saga creates an Order's Shipment with Orchestration's token: once per Order, from
 * the Warehouse assigned to its Shipping address, with each line's Product name and Serial tracking
 * from Catalog's Products.
 */
class CreateShipmentApiTest extends ShipmentsApiTest {

  @Test
  void aShipmentAwaitsPickFromTheWarehouseInItsCountryWithItsProductsNamed() {
    var quantities = new LinkedHashMap<String, Integer>();
    quantities.put(PHONE, 1);
    quantities.put(LAPTOP, 1);
    quantities.put(CASE, 2);

    var shipment = created("DE", quantities);

    assertThat(shipment.id()).isNotBlank();
    assertThat(shipment.customerId()).isEqualTo("customer-42");
    assertThat(shipment.warehouseId()).isEqualTo("WH-BER");
    assertThat(shipment.status()).isEqualTo("AWAITING_PICK");
    assertThat(shipment.lines())
        .extracting(
            LineView::variantId,
            LineView::productName,
            LineView::quantity,
            LineView::serialTracking,
            LineView::serialNumbers)
        .containsExactly(
            tuple(PHONE, "Pixel 9", 1, "IMEI", java.util.List.of()),
            tuple(LAPTOP, "ThinkPad X1", 1, "SERIAL", java.util.List.of()),
            tuple(CASE, "Pixel 9 Case", 2, "NONE", java.util.List.of()));
    assertThat(shipment.shippingAddress())
        .containsEntry("recipientName", "Erika Mustermann")
        .containsEntry("country", "DE");
    assertThat(shipment.shippingOption())
        .isEqualTo(new OptionView("EXPRESS", "Mock Carrier", new MoneyView(999, "EUR"), 1));
    assertThat(shipment.carrier()).isNull();
    assertThat(shipment.trackingNumber()).isNull();
    assertThat(shipment.trackingEvents()).isEmpty();
    assertThat(shipment.statusHistory())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.status()).isEqualTo("AWAITING_PICK");
              assertThat(entry.by()).isEqualTo("ORCHESTRATION");
              assertThat(entry.at()).isEqualTo(shipment.createdAt());
            });
    assertThat(read(STAFF, shipment.id())).isEqualTo(shipment);
  }

  @Test
  void aDutchAddressShipsFromAmsterdam() {
    assertThat(created("NL", Map.of(CASE, 1)).warehouseId()).isEqualTo("WH-AMS");
  }

  @Test
  void anAddressInACountryWithNoWarehouseShipsFromTheDefault() {
    assertThat(created("FR", Map.of(CASE, 1)).warehouseId()).isEqualTo("WH-BER");
  }

  @Test
  void aSecondShipmentForTheSameOrderIsAConflict() {
    var orderId = UUID.randomUUID().toString();
    createdFrom(shipmentRequest(orderId, "customer-42", "DE", lines(Map.of(CASE, 1))));

    create(shipmentRequest(orderId, "customer-42", "DE", lines(Map.of(CASE, 1))))
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("shipmentExists");
  }

  @Test
  void theSameKeyAgainReplaysTheFirstAnswerAndCreatesNothingMore() {
    var key = UUID.randomUUID().toString();
    var orderId = UUID.randomUUID().toString();
    var body = shipmentRequest(orderId, "customer-42", "DE", lines(Map.of(CASE, 1)));
    var first =
        createWithKey(key, body)
            .expectStatus()
            .isCreated()
            .expectBody(ShipmentView.class)
            .returnResult()
            .getResponseBody();

    createWithKey(key, body)
        .expectStatus()
        .isCreated()
        .expectHeader()
        .valueEquals("Idempotent-Replayed", "true")
        .expectBody(ShipmentView.class)
        .isEqualTo(first);
  }

  @Test
  void aVariantTheProductProjectionDoesNotKnowYetIsUnavailableForNow() {
    create(
            shipmentRequest(
                UUID.randomUUID().toString(),
                "customer-42",
                "DE",
                lines(Map.of("UNKNOWN-" + UUID.randomUUID(), 1))))
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .exists("Retry-After")
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("productsNotKnownYet");
  }

  @Test
  void aCreateWithoutAnIdempotencyKeyIsRefused() {
    http.post()
        .uri("/shipments")
        .headers(h -> h.setBearerAuth(ORCHESTRATION))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            shipmentRequest(
                UUID.randomUUID().toString(), "customer-42", "DE", lines(Map.of(CASE, 1))))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("idempotencyKeyRequired");
  }

  @ParameterizedTest
  @ValueSource(strings = {"STAFF", "CUSTOMER", "CHECKOUT"})
  void onlyOrchestrationCreatesShipments(String role) {
    http.post()
        .uri("/shipments")
        .headers(h -> h.setBearerAuth(com.ecomm.commons.security.FakeKeycloak.token("x", role)))
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            shipmentRequest(
                UUID.randomUUID().toString(), "customer-42", "DE", lines(Map.of(CASE, 1))))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"customerId\": \"c\", \"lines\": [{\"variantId\": \"A\", \"quantity\": 1}]}|orderId",
        "{\"orderId\": \"o\", \"lines\": [{\"variantId\": \"A\", \"quantity\": 1}]}|customerId",
        "{\"orderId\": \"o\", \"customerId\": \"c\", \"lines\": []}|lines",
        "{\"orderId\": \"o\", \"customerId\": \"c\", \"lines\": [{\"variantId\": \"A\","
            + " \"quantity\": 0}]}|lines[0].quantity",
        "{\"orderId\": \"o\", \"customerId\": \"c\", \"lines\": [{\"variantId\": \"A\","
            + " \"quantity\": 1}, {\"variantId\": \"A\", \"quantity\": 1}]}|lines[1].variantId"
      })
  void aMalformedRequestIsABadRequestNamingTheField(String requestAndField) {
    var parts = requestAndField.split("\\|");
    var partial = parts[0];
    var body =
        partial.substring(0, partial.length() - 1)
            + ", \"shippingAddress\": "
            + address("DE")
            + ", \"shippingOption\": "
            + EXPRESS
            + "}";
    create(body)
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo(parts[1]);
  }

  @Test
  void aShippingAddressWithoutACountryIsABadRequest() {
    create(
            """
            {"orderId": "o-%s", "customerId": "c", "lines": [{"variantId": "A", "quantity": 1}],
             "shippingAddress": {"recipientName": "E", "line1": "L", "postcode": "1", "city": "C"},
             "shippingOption": %s}
            """
                .formatted(UUID.randomUUID(), EXPRESS))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("shippingAddress.country");
  }
}
