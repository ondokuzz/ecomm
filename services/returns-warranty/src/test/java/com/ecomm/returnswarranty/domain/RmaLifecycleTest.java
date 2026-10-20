package com.ecomm.returnswarranty.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.returnswarranty.domain.RmaExceptions.OutcomeNotForKindException;
import com.ecomm.returnswarranty.domain.RmaExceptions.QuantityMismatchException;
import com.ecomm.returnswarranty.domain.RmaExceptions.ResolutionContradictsInspectionException;
import com.ecomm.returnswarranty.domain.RmaExceptions.RmaAlreadyDecidedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialMismatchException;
import com.ecomm.returnswarranty.domain.RmaExceptions.TransitionRefusedException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

/**
 * An RMA from its request to its resolution: each status it may move to from the one it is in,
 * every other refused; its Send-back deadline; receiving exactly its units; inspecting it for its
 * kind; and the RMA history every step appends to.
 */
class RmaLifecycleTest {

  private static final Instant DELIVERED_AT = Instant.parse("2026-03-01T10:00:00Z");
  private static final Instant REQUESTED_AT = Instant.parse("2026-03-05T10:00:00Z");
  private static final Instant LATER = Instant.parse("2026-03-06T10:00:00Z");
  private static final Duration RETURN_PERIOD = Duration.ofDays(14);
  private static final Duration SEND_BACK_PERIOD = Duration.ofDays(14);
  private static final String IMEI_1 = "490154203237518";
  private static final String IMEI_2 = "356938035643809";

  private static final Delivery DELIVERY =
      new Delivery(
          UUID.randomUUID(),
          "order-1",
          "customer-42",
          "WH-BER",
          DELIVERED_AT,
          List.of(
              new DeliveredLine(
                  "PHONE", "Galaxy S25", 2, SerialTracking.IMEI, List.of(IMEI_1, IMEI_2)),
              new DeliveredLine("CASE", "Phone case", 3, SerialTracking.NONE, List.of())));

  private static final Order ORDER =
      new Order(
          "order-1",
          1,
          "EUR",
          List.of(new OrderLine("PHONE", 2, 79900), new OrderLine("CASE", 3, 1500)),
          0,
          0);

  private static Rma requested(RmaKind kind, String variantId, int quantity, String... serials) {
    return Rma.request(
        UUID.randomUUID(),
        new RmaRequest(
            "customer-42", "order-1", variantId, quantity, List.of(serials), kind, "Broken"),
        DELIVERY,
        List.of(),
        RETURN_PERIOD,
        Optional.of(new WarrantyTerms(24, "Samsung")),
        ORDER,
        REQUESTED_AT);
  }

  private static Rma phoneReturn() {
    return requested(RmaKind.RETURN, "PHONE", 1, IMEI_1);
  }

  private static Rma approved(Rma rma) {
    return rma.approve("staff-7", null, REQUESTED_AT, SEND_BACK_PERIOD, "WH-BER");
  }

  private static Rma received(Rma rma) {
    return approved(rma).receive("staff-7", rma.serialNumbers(), rma.quantity(), LATER);
  }

  private static Rma inspected(Rma rma, InspectionOutcome outcome) {
    return received(rma).inspect("staff-7", outcome, "Looked at it", LATER);
  }

  @Test
  void aRequestIsTheFirstEntryOfItsHistoryAndKeepsItsRefundAmount() {
    var rma = requested(RmaKind.RETURN, "CASE", 2);

    assertThat(rma.history())
        .containsExactly(new RmaHistoryEntry(RmaStatus.REQUESTED, "CUSTOMER", REQUESTED_AT, null));
    assertThat(rma.refundAmount()).isEqualTo(new Money(3000, "EUR"));
  }

  @Test
  void anApprovalSetsTheSendBackDeadlineFromTheApprovalWhenTheWindowEndsSooner() {
    var rma = approved(phoneReturn());

    // The Return Period ends on 15 March; 14 days after approval is 19 March.
    assertThat(rma.status()).isEqualTo(RmaStatus.APPROVED);
    assertThat(rma.sendBackBy()).isEqualTo(Instant.parse("2026-03-19T10:00:00Z"));
    assertThat(rma.warehouseId()).isEqualTo("WH-BER");
  }

  @Test
  void anApprovalSetsTheSendBackDeadlineToTheWindowsEndWhenItIsLater() {
    var claim = requested(RmaKind.WARRANTY_CLAIM, "PHONE", 1, IMEI_1);

    var rma = approved(claim);

    assertThat(rma.sendBackBy()).isEqualTo(Instant.parse("2028-03-01T10:00:00Z"));
  }

  @Test
  void aReturnMovesFromRequestToRefundAndEveryStepIsInItsHistory() {
    var rma =
        inspected(phoneReturn(), InspectionOutcome.ACCEPTED)
            .resolve(RmaStatus.REFUNDED, "re_123", null, null, LATER);

    assertThat(rma.status()).isEqualTo(RmaStatus.REFUNDED);
    assertThat(rma.resolution()).isEqualTo(new Resolution("re_123", null));
    assertThat(rma.inspection())
        .isEqualTo(new Inspection(InspectionOutcome.ACCEPTED, "Looked at it"));
    assertThat(rma.history())
        .extracting(RmaHistoryEntry::status, RmaHistoryEntry::by)
        .containsExactly(
            Tuple.tuple(RmaStatus.REQUESTED, "CUSTOMER"),
            Tuple.tuple(RmaStatus.APPROVED, "staff-7"),
            Tuple.tuple(RmaStatus.RECEIVED, "staff-7"),
            Tuple.tuple(RmaStatus.INSPECTED, "staff-7"),
            Tuple.tuple(RmaStatus.REFUNDED, "ORCHESTRATION"));
    assertThat(rma.version()).isEqualTo(5);
  }

  @Test
  void aConfirmedFaultIsExchangedForAReplacementShipment() {
    var shipmentId = UUID.randomUUID();
    var claim = requested(RmaKind.WARRANTY_CLAIM, "PHONE", 1, IMEI_2);

    var rma =
        inspected(claim, InspectionOutcome.FAULT_CONFIRMED)
            .resolve(RmaStatus.EXCHANGED, null, shipmentId, null, LATER);

    assertThat(rma.status()).isEqualTo(RmaStatus.EXCHANGED);
    assertThat(rma.resolution()).isEqualTo(new Resolution(null, shipmentId));
  }

  @Test
  void aConfirmedFaultWithoutAReplacementIsRefundedWithANote() {
    var claim = requested(RmaKind.WARRANTY_CLAIM, "PHONE", 1, IMEI_2);

    var rma =
        inspected(claim, InspectionOutcome.FAULT_CONFIRMED)
            .resolve(RmaStatus.REFUNDED, "re_9", null, "No replacement in Stock", LATER);

    assertThat(rma.history().getLast().note()).isEqualTo("No replacement in Stock");
  }

  @Test
  void aResolutionMustFollowFromTheInspection() {
    var refused = inspected(phoneReturn(), InspectionOutcome.REFUSED);
    var accepted = inspected(phoneReturn(), InspectionOutcome.ACCEPTED);

    assertThatThrownBy(() -> refused.resolve(RmaStatus.REFUNDED, "re_1", null, null, LATER))
        .isInstanceOf(ResolutionContradictsInspectionException.class);
    assertThatThrownBy(
            () -> accepted.resolve(RmaStatus.EXCHANGED, null, UUID.randomUUID(), null, LATER))
        .isInstanceOf(ResolutionContradictsInspectionException.class);
    assertThatThrownBy(() -> accepted.resolve(RmaStatus.CLOSED, null, null, null, LATER))
        .isInstanceOf(ResolutionContradictsInspectionException.class);
    assertThat(refused.resolve(RmaStatus.CLOSED, null, null, "Water damage", LATER).status())
        .isEqualTo(RmaStatus.CLOSED);
  }

  @Test
  void anInspectedRmaCanBeHeldWithTheReason() {
    var rma =
        inspected(phoneReturn(), InspectionOutcome.ACCEPTED).hold("refund_window_closed", LATER);

    assertThat(rma.status()).isEqualTo(RmaStatus.ON_HOLD);
    assertThat(rma.history().getLast())
        .isEqualTo(
            new RmaHistoryEntry(RmaStatus.ON_HOLD, "ORCHESTRATION", LATER, "refund_window_closed"));
  }

  @Test
  void onlyAnApprovedRmaExpires() {
    var rma = approved(phoneReturn()).expire(LATER);

    assertThat(rma.status()).isEqualTo(RmaStatus.EXPIRED);
    assertThatThrownBy(() -> received(phoneReturn()).expire(LATER))
        .isInstanceOf(TransitionRefusedException.class);
  }

  @Test
  void everyOtherTransitionIsRefused() {
    var requested = phoneReturn();
    var approved = approved(requested);
    var received = received(requested);
    var inspected = inspected(requested, InspectionOutcome.ACCEPTED);
    var refunded = inspected.resolve(RmaStatus.REFUNDED, "re_1", null, null, LATER);
    var held = inspected.hold("declined", LATER);
    var expired = approved.expire(LATER);

    for (var rma : List.of(requested, received, inspected, refunded, held, expired)) {
      assertThatThrownBy(() -> rma.receive("staff-7", List.of(IMEI_1), 1, LATER))
          .as(rma.status().name())
          .isInstanceOf(TransitionRefusedException.class);
    }
    for (var rma : List.of(requested, approved, inspected, refunded, held, expired)) {
      assertThatThrownBy(() -> rma.inspect("staff-7", InspectionOutcome.ACCEPTED, null, LATER))
          .as(rma.status().name())
          .isInstanceOf(TransitionRefusedException.class);
    }
    for (var rma : List.of(requested, approved, received, refunded, held, expired)) {
      assertThatThrownBy(() -> rma.resolve(RmaStatus.REFUNDED, "re_2", null, null, LATER))
          .as(rma.status().name())
          .isInstanceOf(TransitionRefusedException.class);
      assertThatThrownBy(() -> rma.hold("declined", LATER))
          .as(rma.status().name())
          .isInstanceOf(TransitionRefusedException.class);
    }
    for (var rma : List.of(requested, received, inspected, refunded, held, expired)) {
      assertThatThrownBy(() -> rma.expire(LATER))
          .as(rma.status().name())
          .isInstanceOf(TransitionRefusedException.class);
    }
    for (var rma : List.of(approved, received, refunded, expired)) {
      assertThatThrownBy(() -> approved(rma)).isInstanceOf(RmaAlreadyDecidedException.class);
    }
  }

  @Test
  void receivingNamesEachSerialNumberThatIsntTheRmas() {
    var rma = approved(requested(RmaKind.RETURN, "PHONE", 2, IMEI_1, IMEI_2));

    assertThatThrownBy(() -> rma.receive("staff-7", List.of(IMEI_1, "SN-WRONG"), 2, LATER))
        .isInstanceOfSatisfying(
            SerialMismatchException.class,
            e -> assertThat(e.serialNumbers()).containsExactly("SN-WRONG"));
    assertThatThrownBy(() -> rma.receive("staff-7", List.of(IMEI_1), 1, LATER))
        .isInstanceOfSatisfying(
            QuantityMismatchException.class, e -> assertThat(e.expected()).isEqualTo(2));
    assertThat(rma.receive("staff-7", List.of(IMEI_2, " " + IMEI_1), 2, LATER).status())
        .isEqualTo(RmaStatus.RECEIVED);
  }

  @Test
  void receivingUnitsWithoutSerialTrackingConfirmsTheQuantity() {
    var rma = approved(requested(RmaKind.RETURN, "CASE", 2));

    assertThatThrownBy(() -> rma.receive("staff-7", List.of(), 3, LATER))
        .isInstanceOf(QuantityMismatchException.class);
    assertThatThrownBy(() -> rma.receive("staff-7", List.of("SN-1"), 1, LATER))
        .isInstanceOf(SerialMismatchException.class);
    assertThat(rma.receive("staff-7", List.of(), 2, LATER).status()).isEqualTo(RmaStatus.RECEIVED);
  }

  @Test
  void anInspectionsOutcomeMustBeForTheRmasKind() {
    var aReturn = received(phoneReturn());
    var aClaim = received(requested(RmaKind.WARRANTY_CLAIM, "PHONE", 1, IMEI_2));

    assertThatThrownBy(
            () -> aReturn.inspect("staff-7", InspectionOutcome.FAULT_CONFIRMED, null, LATER))
        .isInstanceOf(OutcomeNotForKindException.class);
    assertThatThrownBy(() -> aClaim.inspect("staff-7", InspectionOutcome.ACCEPTED, null, LATER))
        .isInstanceOf(OutcomeNotForKindException.class);
  }

  @Test
  void unitsAreHeldUntilTheRmaIsRejectedExpiredOrClosed() {
    var refused = inspected(phoneReturn(), InspectionOutcome.REFUSED);

    assertThat(phoneReturn().holdsUnits()).isTrue();
    assertThat(approved(phoneReturn()).holdsUnits()).isTrue();
    assertThat(refused.holdsUnits()).isTrue();
    assertThat(refused.hold("x", LATER).holdsUnits()).isTrue();
    assertThat(refused.resolve(RmaStatus.CLOSED, null, null, "No", LATER).holdsUnits()).isFalse();
    assertThat(approved(phoneReturn()).expire(LATER).holdsUnits()).isFalse();
    assertThat(phoneReturn().reject("staff-7", "No", LATER).holdsUnits()).isFalse();
  }
}
