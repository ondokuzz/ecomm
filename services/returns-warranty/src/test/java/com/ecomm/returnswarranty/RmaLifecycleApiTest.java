package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * An approved RMA from receipt to resolution: Staff receive its units, checked against it, and
 * inspect them; the returns Saga resolves it, holds it or expires it. Each step is in its RMA
 * history, and any step its status doesn't allow is a 409.
 */
class RmaLifecycleApiTest extends RmasApiTest {

  private RmaView approvedPhoneReturn(String... imeis) {
    var phone = newVariant("PHONE");
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(phone, "Galaxy S25", imeis.length, "IMEI", imeis)
            .delivered(RECENTLY);
    return approved(requested(rma(shipment.orderId(), phone, imeis.length, "RETURN", imeis)));
  }

  private RmaView approvedChargers(int quantity, String kind) {
    var charger = newVariant("CHARGER");
    if (kind.equals("WARRANTY_CLAIM")) {
      Products.product("Charger", charger).warranty(24, "Anker").publish();
    }
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(charger, "Charger", quantity, "NONE")
            .delivered(RECENTLY);
    return approved(requested(rma(shipment.orderId(), charger, quantity, kind)));
  }

  @Test
  void staffReceiveAnApprovedRmaByItsSerialNumbersWhateverTheirCase() {
    var imei = newImei();
    var other = newImei();
    var rma = approvedPhoneReturn(imei, other);

    var received =
        body(
            step(
                    STAFF,
                    rma.id(),
                    "receive",
                    "{\"serialNumbers\": [\"%s\", \" %s \"]}".formatted(other, imei))
                .expectStatus()
                .isOk());

    assertThat(received.status()).isEqualTo("RECEIVED");
    assertThat(received.history().getLast())
        .isEqualTo(new HistoryView("RECEIVED", "staff-7", "2026-10-15T12:00:00Z", null));
  }

  @Test
  void aSerialNumberThatIsntTheRmasIsRefusedByName() {
    var imei = newImei();
    var rma = approvedPhoneReturn(imei, newImei());
    var stranger = newImei();

    step(
            STAFF,
            rma.id(),
            "receive",
            "{\"serialNumbers\": [\"%s\", \"%s\"]}".formatted(imei, stranger))
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("serialMismatch")
        .jsonPath("$.serialNumbers[0]")
        .isEqualTo(stranger);
    step(STAFF, rma.id(), "receive", "{\"serialNumbers\": [\"%s\"]}".formatted(imei))
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("quantityMismatch")
        .jsonPath("$.expected")
        .isEqualTo(2);
    assertThat(readRma(STAFF, rma.id()).status()).isEqualTo("APPROVED");
  }

  @Test
  void unitsWithoutSerialTrackingAreReceivedByQuantity() {
    var rma = approvedChargers(2, "RETURN");

    step(STAFF, rma.id(), "receive", "{\"quantity\": 3}")
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("quantityMismatch");

    assertThat(body(step(STAFF, rma.id(), "receive", "{\"quantity\": 2}")).status())
        .isEqualTo("RECEIVED");
  }

  @Test
  void aReceiptNamesSerialNumbersOrAQuantity() {
    var rma = approvedChargers(1, "RETURN");

    for (var body : new String[] {"{}", "{\"quantity\": 1, \"serialNumbers\": [\"A\"]}"}) {
      step(STAFF, rma.id(), "receive", body)
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.errors[0].field")
          .isEqualTo("serialNumbers");
    }
  }

  @Test
  void staffInspectAReceivedReturnAndARefusalNeedsANote() {
    var rma = received(approvedChargers(1, "RETURN"));

    step(STAFF, rma.id(), "inspect", "{\"outcome\": \"REFUSED\"}")
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("note");
    step(STAFF, rma.id(), "inspect", "{\"outcome\": \"FAULT_CONFIRMED\"}")
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("outcomeNotForKind");

    var inspected =
        body(
            step(STAFF, rma.id(), "inspect", "{\"outcome\": \"REFUSED\", \"note\": \"Worn\"}")
                .expectStatus()
                .isOk());

    assertThat(inspected.status()).isEqualTo("INSPECTED");
    assertThat(inspected.inspection()).isEqualTo(Map.of("outcome", "REFUSED", "note", "Worn"));
    assertThat(inspected.history().getLast().note()).isEqualTo("Worn");
  }

  @Test
  void anAcceptedReturnIsRefundedAndSaysWhenItsOrderIsFullyRefunded() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 2, "NONE").delivered(RECENTLY);
    var first =
        inspected(
            received(approved(requested(rma(shipment.orderId(), charger, 1, "RETURN")))),
            "ACCEPTED");
    var second =
        inspected(
            received(approved(requested(rma(shipment.orderId(), charger, 1, "RETURN")))),
            "ACCEPTED");

    step(
            ORCHESTRATION,
            first.id(),
            "resolve",
            "{\"resolution\": \"REFUNDED\", \"paymentTransactionReference\": \"re_1\"}")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("REFUNDED")
        .jsonPath("$.resolution.paymentTransactionReference")
        .isEqualTo("re_1")
        .jsonPath("$.orderFullyRefunded")
        .isEqualTo(false);
    step(
            ORCHESTRATION,
            second.id(),
            "resolve",
            "{\"resolution\": \"REFUNDED\", \"paymentTransactionReference\": \"re_2\"}")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.orderFullyRefunded")
        .isEqualTo(true);

    var refunded = readRma(CUSTOMER, first.id());
    assertThat(refunded.history().getLast())
        .isEqualTo(new HistoryView("REFUNDED", "ORCHESTRATION", "2026-10-15T12:00:00Z", null));
  }

  @Test
  void aConfirmedFaultIsExchangedForItsReplacementShipment() {
    var rma = inspected(received(approvedChargers(1, "WARRANTY_CLAIM")), "FAULT_CONFIRMED");
    var replacement = UUID.randomUUID().toString();

    step(ORCHESTRATION, rma.id(), "resolve", "{\"resolution\": \"EXCHANGED\"}")
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("replacementShipmentId");
    var exchanged =
        body(
            step(
                    ORCHESTRATION,
                    rma.id(),
                    "resolve",
                    "{\"resolution\": \"EXCHANGED\", \"replacementShipmentId\": \"%s\"}"
                        .formatted(replacement))
                .expectStatus()
                .isOk());

    assertThat(exchanged.status()).isEqualTo("EXCHANGED");
    assertThat(exchanged.resolution()).isEqualTo(Map.of("replacementShipmentId", replacement));
  }

  @Test
  void aResolutionMustFollowFromTheInspection() {
    var rma = inspected(received(approvedChargers(1, "RETURN")), "ACCEPTED");

    step(ORCHESTRATION, rma.id(), "resolve", "{\"resolution\": \"CLOSED\"}")
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("resolutionContradictsInspection");
    step(ORCHESTRATION, rma.id(), "resolve", "{\"resolution\": \"ON_HOLD\"}")
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void aRefusedReturnIsClosedAndItsUnitCanBeClaimedUnderWarranty() {
    var phone = newVariant("PHONE");
    Products.product("Galaxy S25", phone).tracked("IMEI").warranty(24, "Samsung").publish();
    var imei = newImei();
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(phone, "Galaxy S25", 1, "IMEI", imei)
            .delivered(RECENTLY);
    var rma =
        inspected(
            received(approved(requested(rma(shipment.orderId(), phone, 1, "RETURN", imei)))),
            "REFUSED");

    var closed =
        body(
            step(
                    ORCHESTRATION,
                    rma.id(),
                    "resolve",
                    "{\"resolution\": \"CLOSED\", \"note\": \"Checked\"}")
                .expectStatus()
                .isOk());

    assertThat(closed.status()).isEqualTo("CLOSED");
    assertThat(requested(rma(shipment.orderId(), phone, 1, "WARRANTY_CLAIM", imei)).status())
        .isEqualTo("REQUESTED");
  }

  @Test
  void theReturnsSagaHoldsAnInspectedRmaWithTheReason() {
    var rma = inspected(received(approvedChargers(1, "RETURN")), "ACCEPTED");

    var held =
        body(
            step(ORCHESTRATION, rma.id(), "hold", "{\"reason\": \"refund_window_closed\"}")
                .expectStatus()
                .isOk());

    assertThat(held.status()).isEqualTo("ON_HOLD");
    assertThat(held.history().getLast().note()).isEqualTo("refund_window_closed");
    step(
            ORCHESTRATION,
            rma.id(),
            "resolve",
            "{\"resolution\": \"REFUNDED\", \"paymentTransactionReference\": \"re_1\"}")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("transitionRefused")
        .jsonPath("$.status")
        .isEqualTo("ON_HOLD");
  }

  @Test
  void onlyAnApprovedRmaExpiresSoAReceiptThatRacedTheTimerWins() {
    var expiring = approvedChargers(1, "RETURN");
    var receivedFirst = received(approvedChargers(1, "RETURN"));

    var expired = body(step(ORCHESTRATION, expiring.id(), "expire", "{}").expectStatus().isOk());

    assertThat(expired.status()).isEqualTo("EXPIRED");
    assertThat(expired.history().getLast().by()).isEqualTo("ORCHESTRATION");
    step(ORCHESTRATION, receivedFirst.id(), "expire", "{}")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("transitionRefused");
    step(STAFF, expiring.id(), "receive", "{\"quantity\": 1}").expectStatus().isEqualTo(409);
  }

  @Test
  void aReceivedRmaCantBeReceivedAgainUnderAnotherKey() {
    var rma = received(approvedChargers(1, "RETURN"));

    step(STAFF, rma.id(), "receive", "{\"quantity\": 1}")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("RECEIVED");
    step(STAFF, rma.id(), "inspect", "{\"outcome\": \"ACCEPTED\"}").expectStatus().isOk();
    step(STAFF, rma.id(), "inspect", "{\"outcome\": \"ACCEPTED\"}").expectStatus().isEqualTo(409);
  }

  @Test
  void aStepRepeatedWithItsKeyIsAnsweredAgainAndTakenOnce() {
    var rma = approvedChargers(1, "RETURN");
    var key = UUID.randomUUID().toString();

    var first = body(stepWithKey(STAFF, key, rma.id(), "receive", "{\"quantity\": 1}"));
    var again = stepWithKey(STAFF, key, rma.id(), "receive", "{\"quantity\": 1}");

    again.expectStatus().isOk().expectHeader().valueEquals("Idempotent-Replayed", "true");
    assertThat(body(again)).isEqualTo(first);
    assertThat(readRma(STAFF, rma.id()).history())
        .extracting(HistoryView::status)
        .containsExactly("REQUESTED", "APPROVED", "RECEIVED");
  }

  @Test
  void eachStepIsForItsCallerAloneAndTakesAKey() {
    var rma = approvedChargers(1, "RETURN");

    step(CUSTOMER, rma.id(), "receive", "{\"quantity\": 1}").expectStatus().isForbidden();
    step(ORCHESTRATION, rma.id(), "receive", "{\"quantity\": 1}").expectStatus().isForbidden();
    step(ORCHESTRATION, rma.id(), "inspect", "{\"outcome\": \"ACCEPTED\"}")
        .expectStatus()
        .isForbidden();
    for (var orchestrations : new String[] {"resolve", "hold", "expire"}) {
      step(STAFF, rma.id(), orchestrations, "{}").expectStatus().isForbidden();
      step(CUSTOMER, rma.id(), orchestrations, "{}").expectStatus().isForbidden();
    }
    http.post()
        .uri("/rmas/{id}/receive", rma.id())
        .headers(h -> h.setBearerAuth(STAFF))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"quantity\": 1}")
        .exchange()
        .expectStatus()
        .isBadRequest();
    step(ORCHESTRATION, "6f1c0e8a-1111-4222-8333-944455556666", "expire", "{}")
        .expectStatus()
        .isNotFound();
  }
}
