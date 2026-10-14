package com.ecomm.fulfillmentshipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A Shipment moves {@code AWAITING_PICK → PICKED → PACKED → SHIPPED}, or {@code PACKED → ON_HOLD}:
 * Staff pick and pack, and the fulfillment Saga dispatches or holds. Each move appends who made it
 * to the status history; any other move is a 409 that changes nothing.
 */
class ShipmentTransitionApiTest extends ShipmentsApiTest {

  @Test
  void staffPickAShipmentAwaitingPick() {
    var shipment = created();

    var picked = body(pick(shipment.id()).expectStatus().isOk());

    assertThat(picked.status()).isEqualTo("PICKED");
    assertThat(picked.statusHistory())
        .extracting(HistoryView::status, HistoryView::by)
        .containsExactly(tuple("AWAITING_PICK", "ORCHESTRATION"), tuple("PICKED", "staff-7"));
    assertThat(read(STAFF, shipment.id())).isEqualTo(picked);
  }

  @Test
  void dispatchBooksThePackedShipmentWithTheCarrierAndShipsIt() {
    var packed = packed();

    var shipped = body(dispatch(packed.id()).expectStatus().isOk());

    assertThat(shipped.status()).isEqualTo("SHIPPED");
    assertThat(shipped.carrier()).isEqualTo("Mock Carrier");
    assertThat(shipped.trackingNumber()).matches("MC[0-9]{10}");
    assertThat(shipped.statusHistory())
        .extracting(HistoryView::status, HistoryView::by)
        .containsExactly(
            tuple("AWAITING_PICK", "ORCHESTRATION"),
            tuple("PICKED", "staff-7"),
            tuple("PACKED", "staff-7"),
            tuple("SHIPPED", "ORCHESTRATION"));
    assertThat(read(STAFF, packed.id())).isEqualTo(shipped);
  }

  @Test
  void aDispatchRetriedWithItsKeyBooksOnce() {
    var packed = packed();
    var key = UUID.randomUUID().toString();

    var first = body(dispatchWithKey(packed.id(), key).expectStatus().isOk());
    var again = body(dispatchWithKey(packed.id(), key).expectStatus().isOk());

    assertThat(again).isEqualTo(first);
    assertThat(carrierWebhooks.about(first.trackingNumber())).hasSize(3);
  }

  @Test
  void holdPutsAPackedShipmentOnHoldWithTheReason() {
    var packed = packed();

    var held = body(hold(packed.id(), "authorization_expired").expectStatus().isOk());

    assertThat(held.status()).isEqualTo("ON_HOLD");
    assertThat(held.holdReason()).isEqualTo("authorization_expired");
    assertThat(held.carrier()).isNull();
    assertThat(held.statusHistory().getLast())
        .extracting(HistoryView::status, HistoryView::by)
        .containsExactly("ON_HOLD", "ORCHESTRATION");
  }

  @Test
  void aHoldNeedsAReason() {
    hold(packed().id(), "").expectStatus().isBadRequest();
  }

  @Test
  void pickingTwiceIsAConflict() {
    var shipment = created();
    pick(shipment.id()).expectStatus().isOk();

    expectIllegal(pick(shipment.id()));
  }

  @Test
  void packingAShipmentNotPickedIsAConflict() {
    var shipment = created();

    expectIllegal(pack(shipment.id(), serials(Map.of(PHONE, List.of(newImei())))));
    assertThat(read(STAFF, shipment.id())).isEqualTo(shipment);
  }

  @Test
  void dispatchingOrHoldingAShipmentNotPackedIsAConflict() {
    var shipment = created();
    pick(shipment.id()).expectStatus().isOk();

    expectIllegal(dispatch(shipment.id()));
    expectIllegal(hold(shipment.id(), "authorization_expired"));
    assertThat(read(STAFF, shipment.id()).status()).isEqualTo("PICKED");
  }

  @Test
  void aShipmentOnHoldIsFinal() {
    var held = packed();
    hold(held.id(), "authorization_expired").expectStatus().isOk();

    expectIllegal(dispatch(held.id()));
    expectIllegal(hold(held.id(), "again"));
    expectIllegal(pick(held.id()));
  }

  @Test
  void aShippedShipmentCanNotBeHeldOrDispatchedAgain() {
    var shipped = shipped();

    expectIllegal(hold(shipped.id(), "authorization_expired"));
    expectIllegal(dispatch(shipped.id()));
  }

  @Test
  void anUnknownShipmentIsNotFound() {
    pick(UUID.randomUUID().toString()).expectStatus().isNotFound();
    pick("not-a-uuid").expectStatus().isNotFound();
    dispatch(UUID.randomUUID().toString()).expectStatus().isNotFound();
  }

  @Test
  void onlyStaffPickAndPack() {
    var shipment = created();
    for (var token :
        List.of(CUSTOMER, ORCHESTRATION, CHECKOUT, FakeKeycloak.token("customer-42", "CUSTOMER"))) {
      http.post()
          .uri("/shipments/{id}/pick", shipment.id())
          .headers(h -> h.setBearerAuth(token))
          .exchange()
          .expectStatus()
          .isForbidden();
      http.post()
          .uri("/shipments/{id}/pack", shipment.id())
          .headers(h -> h.setBearerAuth(token))
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
          .body("{\"serialNumbers\": {}}")
          .exchange()
          .expectStatus()
          .isForbidden();
    }
  }

  @Test
  void onlyOrchestrationDispatchesAndHolds() {
    var packed = packed();
    for (var token : List.of(STAFF, CUSTOMER, CHECKOUT)) {
      http.post()
          .uri("/shipments/{id}/dispatch", packed.id())
          .headers(h -> h.setBearerAuth(token))
          .header("Idempotency-Key", UUID.randomUUID().toString())
          .exchange()
          .expectStatus()
          .isForbidden();
      http.post()
          .uri("/shipments/{id}/hold", packed.id())
          .headers(h -> h.setBearerAuth(token))
          .header("Idempotency-Key", UUID.randomUUID().toString())
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
          .body("{\"reason\": \"x\"}")
          .exchange()
          .expectStatus()
          .isForbidden();
    }
  }

  @Test
  void dispatchAndHoldNeedAnIdempotencyKey() {
    var packed = packed();
    http.post()
        .uri("/shipments/{id}/dispatch", packed.id())
        .headers(h -> h.setBearerAuth(ORCHESTRATION))
        .exchange()
        .expectStatus()
        .isBadRequest();
    http.post()
        .uri("/shipments/{id}/hold", packed.id())
        .headers(h -> h.setBearerAuth(ORCHESTRATION))
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body("{\"reason\": \"x\"}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  private static void expectIllegal(
      org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec response) {
    response
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("illegalTransition");
  }
}
