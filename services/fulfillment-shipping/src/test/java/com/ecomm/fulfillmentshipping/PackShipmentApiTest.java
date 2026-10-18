package com.ecomm.fulfillmentshipping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Staff pack a picked Shipment, entering one Serial number per unit of each serial-tracked line: an
 * IMEI with a valid check digit, or a serial number of 4 to 40 letters, digits and hyphens, kept
 * upper-cased. Anything else, the wrong count, or a repeat within the Shipment is a 422 naming the
 * field; one another Shipment carries for the same Product is a 409, unless that Shipment is on
 * hold.
 */
class PackShipmentApiTest extends ShipmentsApiTest {

  @Test
  void packingKeepsEachUnitsSerialNumber() {
    var shipment = picked(Map.of(PHONE, 2, LAPTOP, 1, CASE, 3));
    var imeis = List.of(newImei(), newImei());
    var serial = newSerial();

    var packed =
        body(
            pack(shipment.id(), serials(Map.of(PHONE, imeis, LAPTOP, List.of(serial))))
                .expectStatus()
                .isOk());

    assertThat(packed.status()).isEqualTo("PACKED");
    assertThat(packed.lines())
        .extracting(LineView::variantId, LineView::serialNumbers)
        .containsExactlyInAnyOrder(
            org.assertj.core.api.Assertions.tuple(PHONE, imeis),
            org.assertj.core.api.Assertions.tuple(LAPTOP, List.of(serial.toUpperCase())),
            org.assertj.core.api.Assertions.tuple(CASE, List.of()));
    assertThat(packed.statusHistory().getLast().by()).isEqualTo("staff-7");
    assertThat(read(STAFF, shipment.id())).isEqualTo(packed);
  }

  @Test
  void anImeiThatFailsItsCheckDigitIsRefused() {
    var shipment = picked(Map.of(PHONE, 1));
    var imei = newImei();
    var wrong = imei.substring(0, 14) + (char) ('0' + (imei.charAt(14) - '0' + 1) % 10);

    expectViolation(
        pack(shipment.id(), serials(Map.of(PHONE, List.of(wrong)))),
        "serialNumbers." + PHONE + "[0]");
    assertThat(read(STAFF, shipment.id()).status()).isEqualTo("PICKED");
  }

  @Test
  void aSerialNumberOfTheWrongFormIsRefused() {
    var shipment = picked(Map.of(LAPTOP, 1));

    expectViolation(
        pack(shipment.id(), serials(Map.of(LAPTOP, List.of("SN 1")))),
        "serialNumbers." + LAPTOP + "[0]");
  }

  @Test
  void theWrongNumberOfSerialNumbersIsRefused() {
    var shipment = picked(Map.of(PHONE, 2));

    expectViolation(
        pack(shipment.id(), serials(Map.of(PHONE, List.of(newImei())))), "serialNumbers." + PHONE);
    expectViolation(pack(shipment.id(), serials(Map.of())), "serialNumbers." + PHONE);
  }

  @Test
  void aSerialNumberEnteredTwiceInTheShipmentIsRefused() {
    var shipment = picked(Map.of(LAPTOP, 2));
    var serial = newSerial();

    expectViolation(
        pack(shipment.id(), serials(Map.of(LAPTOP, List.of(serial, serial.toUpperCase())))),
        "serialNumbers." + LAPTOP + "[1]");
  }

  @Test
  void aSerialNumberForAVariantThatIsNotTrackedIsRefused() {
    var shipment = picked(Map.of(CASE, 1));

    expectViolation(
        pack(shipment.id(), serials(Map.of(CASE, List.of(newSerial())))), "serialNumbers." + CASE);
  }

  @Test
  void aSerialNumberAnotherShipmentCarriesIsAConflict() {
    var imei = newImei();
    var first = picked(Map.of(PHONE, 1));
    pack(first.id(), serials(Map.of(PHONE, List.of(imei)))).expectStatus().isOk();
    var second = picked(Map.of(PHONE, 1));

    pack(second.id(), serials(Map.of(PHONE, List.of(imei))))
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("serialAlreadyShipped")
        .jsonPath("$.serialNumber")
        .isEqualTo(imei);
    assertThat(read(STAFF, second.id()).status()).isEqualTo("PICKED");
  }

  @Test
  void aSerialNumberOnAShipmentPutOnHoldCanBePackedOnAnother() {
    var imei = newImei();
    var held = picked(Map.of(PHONE, 1));
    pack(held.id(), serials(Map.of(PHONE, List.of(imei)))).expectStatus().isOk();
    hold(held.id(), "Capture declined: authorization_expired").expectStatus().isOk();
    var second = picked(Map.of(PHONE, 1));

    pack(second.id(), serials(Map.of(PHONE, List.of(imei)))).expectStatus().isOk();
    assertThat(read(STAFF, held.id()).lines().getFirst().serialNumbers()).containsExactly(imei);
  }

  @Test
  void twoProductsUnitsMayShareASerialNumber() {
    var serial = newSerial();
    var laptop = picked(Map.of(LAPTOP, 1));
    pack(laptop.id(), serials(Map.of(LAPTOP, List.of(serial)))).expectStatus().isOk();
    var headphones = picked(Map.of(HEADPHONES, 1));

    pack(headphones.id(), serials(Map.of(HEADPHONES, List.of(serial)))).expectStatus().isOk();
  }

  @Test
  void aShipmentWithNothingTrackedPacksWithNoSerialNumbers() {
    var shipment = picked(Map.of(CASE, 2));

    assertThat(body(pack(shipment.id(), serials(Map.of())).expectStatus().isOk()).status())
        .isEqualTo("PACKED");
  }

  private ShipmentView picked(Map<String, Integer> quantities) {
    var shipment = created("DE", new LinkedHashMap<>(quantities));
    pick(shipment.id()).expectStatus().isOk();
    return shipment;
  }

  private static void expectViolation(RestTestClient.ResponseSpec response, String field) {
    response
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.errors[?(@.field == '%s')]".formatted(field))
        .exists();
  }
}
