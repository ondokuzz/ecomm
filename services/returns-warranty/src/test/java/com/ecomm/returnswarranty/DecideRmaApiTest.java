package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Staff decide an RMA once: approve it, with an optional note, or reject it, with a reason. Each
 * decision is kept in its history.
 */
class DecideRmaApiTest extends RmasApiTest {

  /** A requested return of a delivered charger. */
  private RmaView requestedRma() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 1, "NONE").delivered(RECENTLY);
    return requested(rma(shipment.orderId(), charger, 1, "RETURN"));
  }

  @Test
  void staffApproveAnRmaWithANote() {
    var rma = requestedRma();

    var approved =
        body(approve(STAFF, rma.id(), "{\"note\": \"Send it back\"}").expectStatus().isOk());

    assertThat(approved.status()).isEqualTo("APPROVED");
    assertThat(approved.decisions())
        .containsExactly(
            new DecisionView("APPROVED", "staff-7", "2026-10-15T12:00:00Z", "Send it back"));
    assertThat(readRma(CUSTOMER, rma.id())).isEqualTo(approved);
  }

  @Test
  void anApprovalNeedsNoNote() {
    var rma = requestedRma();

    var approved = body(approve(STAFF, rma.id(), "{}").expectStatus().isOk());

    assertThat(approved.decisions())
        .containsExactly(new DecisionView("APPROVED", "staff-7", "2026-10-15T12:00:00Z", null));
  }

  @Test
  void staffRejectAnRmaWithAReason() {
    var rma = requestedRma();

    var rejected =
        body(reject(STAFF, rma.id(), "{\"reason\": \"Water damage\"}").expectStatus().isOk());

    assertThat(rejected.status()).isEqualTo("REJECTED");
    assertThat(rejected.decisions())
        .containsExactly(
            new DecisionView("REJECTED", "staff-7", "2026-10-15T12:00:00Z", "Water damage"));
  }

  @Test
  void aRejectionNeedsAReason() {
    var rma = requestedRma();

    for (var body : new String[] {"{}", "{\"reason\": \"  \"}"}) {
      reject(STAFF, rma.id(), body)
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.errors[0].field")
          .isEqualTo("reason");
    }
    assertThat(readRma(STAFF, rma.id()).status()).isEqualTo("REQUESTED");
  }

  @Test
  void aDecidedRmaCantBeDecidedAgain() {
    var rma = requestedRma();
    approve(STAFF, rma.id(), "{}").expectStatus().isOk();

    reject(STAFF, rma.id(), "{\"reason\": \"Changed my mind\"}")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("rmaAlreadyDecided");
    approve(STAFF, rma.id(), "{}").expectStatus().isEqualTo(409);

    var read = readRma(STAFF, rma.id());
    assertThat(read.status()).isEqualTo("APPROVED");
    assertThat(read.decisions()).hasSize(1);
  }

  @Test
  void onlyStaffDecide() {
    var rma = requestedRma();

    approve(CUSTOMER, rma.id(), "{}").expectStatus().isForbidden();
    reject(CUSTOMER, rma.id(), "{\"reason\": \"No\"}").expectStatus().isForbidden();
  }

  @Test
  void anUnknownRmaIsNotFound() {
    approve(STAFF, "6f1c0e8a-1111-4222-8333-944455556666", "{}").expectStatus().isNotFound();
    reject(STAFF, "not-an-id", "{\"reason\": \"No\"}").expectStatus().isNotFound();
  }
}
