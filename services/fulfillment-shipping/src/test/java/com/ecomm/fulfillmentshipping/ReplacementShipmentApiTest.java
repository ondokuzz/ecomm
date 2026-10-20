package com.ecomm.fulfillmentshipping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The returns Saga creates a replacement Shipment for an RMA with Orchestration's token: once per
 * RMA, of the Order's units, to the Order's Shipping address. It is a Shipment like any other,
 * marked with its kind and RMA.
 */
class ReplacementShipmentApiTest extends ShipmentsApiTest {

  @Test
  void aReplacementAwaitsPickFromTheWarehouseOfItsAddressNamingItsRma() {
    var rmaId = UUID.randomUUID().toString();
    var orderId = UUID.randomUUID().toString();

    var replacement =
        createdFrom(
            replacementRequest(rmaId, orderId, "customer-42", "NL", lines(Map.of(PHONE, 1))));

    assertThat(replacement.kind()).isEqualTo("REPLACEMENT");
    assertThat(replacement.rmaId()).isEqualTo(rmaId);
    assertThat(replacement.orderId()).isEqualTo(orderId);
    assertThat(replacement.customerId()).isEqualTo("customer-42");
    assertThat(replacement.warehouseId()).isEqualTo("WH-AMS");
    assertThat(replacement.status()).isEqualTo("AWAITING_PICK");
    assertThat(replacement.lines())
        .singleElement()
        .extracting(LineView::productName)
        .isEqualTo("Pixel 9");
    assertThat(read(STAFF, replacement.id())).isEqualTo(replacement);
  }

  @Test
  void anOrdersShipmentIsOfKindOrderWithNoRma() {
    var shipment = created();

    assertThat(shipment.kind()).isEqualTo("ORDER");
    assertThat(shipment.rmaId()).isNull();
  }

  @Test
  void aSecondReplacementForTheSameRmaIsAConflictAnsweringTheFirst() {
    var rmaId = UUID.randomUUID().toString();
    var orderId = UUID.randomUUID().toString();
    var first =
        createdFrom(
            replacementRequest(rmaId, orderId, "customer-42", "DE", lines(Map.of(PHONE, 1))));

    create(replacementRequest(rmaId, orderId, "customer-42", "DE", lines(Map.of(PHONE, 1))))
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("shipmentExists")
        .jsonPath("$.shipment.id")
        .isEqualTo(first.id())
        .jsonPath("$.shipment.rmaId")
        .isEqualTo(rmaId);
  }

  @Test
  void anOrderKeepsOneOrderShipmentBesideItsReplacements() {
    var orderId = UUID.randomUUID().toString();
    var shipment =
        createdFrom(
            shipmentRequest(orderId, "customer-42", "DE", lines(Map.of(PHONE, 1, CASE, 1))));
    var replacement =
        createdFrom(
            replacementRequest(
                UUID.randomUUID().toString(),
                orderId,
                "customer-42",
                "DE",
                lines(Map.of(PHONE, 1))));

    assertThat(replacement.id()).isNotEqualTo(shipment.id());
    create(shipmentRequest(orderId, "customer-42", "DE", lines(Map.of(CASE, 1))))
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.shipment.id")
        .isEqualTo(shipment.id());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"kind\": \"REPLACEMENT\"|rmaId",
        "\"kind\": \"REPLACEMENT\", \"rmaId\": \" \"|rmaId",
        "\"kind\": \"ORDER\", \"rmaId\": \"rma-1\"|rmaId",
        "\"rmaId\": \"rma-1\"|rmaId",
        "\"kind\": \"RETURN\"|kind"
      })
  void aKindAndRmaThatDoNotFitAreABadRequestNamingTheField(String fieldsAndField) {
    var parts = fieldsAndField.split("\\|");
    var body =
        "{%s, \"orderId\": \"%s\", \"customerId\": \"c\", \"lines\": %s, \"shippingAddress\": %s, \"shippingOption\": %s}"
            .formatted(
                parts[0], UUID.randomUUID(), lines(Map.of(PHONE, 1)), address("DE"), EXPRESS);
    create(body)
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo(parts[1]);
  }

  @Test
  void aReplacementIsPickedPackedWithNewSerialNumbersDispatchedAndDelivered() {
    var replacement = replacement();
    pick(replacement.id()).expectStatus().isOk();
    var imei = newImei();

    var packed =
        body(pack(replacement.id(), serials(Map.of(PHONE, List.of(imei)))).expectStatus().isOk());
    var shipped = body(dispatch(replacement.id()).expectStatus().isOk());
    for (var webhook : carrierWebhooks.about(shipped.trackingNumber())) {
      deliver(webhook).expectStatus().isOk();
    }

    assertThat(packed.lines().getFirst().serialNumbers()).containsExactly(imei);
    assertThat(shipped.trackingNumber()).isNotBlank();
    var delivered = read(STAFF, replacement.id());
    assertThat(delivered.status()).isEqualTo("DELIVERED");
    assertThat(delivered.statusHistory())
        .extracting(HistoryView::status)
        .containsExactly("AWAITING_PICK", "PICKED", "PACKED", "SHIPPED", "DELIVERED");
  }

  @Test
  void aReplacementPackedWithAUnitAlreadyShippedIsRefused() {
    var shipped = packed();
    var returnedImei =
        shipped.lines().stream()
            .filter(line -> line.variantId().equals(PHONE))
            .findFirst()
            .orElseThrow()
            .serialNumbers()
            .getFirst();
    var replacement = replacement();
    pick(replacement.id()).expectStatus().isOk();

    pack(replacement.id(), serials(Map.of(PHONE, List.of(returnedImei))))
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("serialAlreadyShipped");
  }
}
