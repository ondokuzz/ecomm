package com.ecomm.orchestration.application;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import com.ecomm.orchestration.application.port.in.ReturnsOutcome;
import com.ecomm.orchestration.application.port.in.ReturnsOutcome.Status;
import com.ecomm.orchestration.application.port.in.ReturnsRequest;
import com.ecomm.orchestration.application.port.in.ReturnsWorkflow;
import com.ecomm.orchestration.application.port.in.RmaChange;
import com.ecomm.orchestration.application.port.in.ShipmentChange;
import com.ecomm.orchestration.application.port.out.OrderReturnActivities;
import com.ecomm.orchestration.application.port.out.RefundActivities;
import com.ecomm.orchestration.application.port.out.RefundActivities.PaymentToRefund;
import com.ecomm.orchestration.application.port.out.RefundActivities.Refund;
import com.ecomm.orchestration.application.port.out.Refusals;
import com.ecomm.orchestration.application.port.out.ReplacementActivities;
import com.ecomm.orchestration.application.port.out.ReturnsInventoryActivities;
import com.ecomm.orchestration.application.port.out.RmaActivities;
import com.ecomm.orchestration.application.port.out.RmaActivities.Expiry;
import com.ecomm.orchestration.application.port.out.RmaActivities.Rma;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.MDC;

/**
 * The returns Saga's steps, in order. As in the other Sagas, every step is an activity, so this
 * code only decides, and replays the same way every time.
 *
 * <p>It reads the RMA first, and takes it up from its status: a run started again once Temporal no
 * longer keeps the first finds it resolved, held or expired, and ends as that one did. Then it
 * waits on the RMA, woken by its events: for its units until the Send-back deadline, a durable
 * timer, expiring it if they don't come; and for their Inspection, with no deadline, reading the
 * RMA again each Inspection check. A replacement's packing is awaited the same way. An event at a
 * version it has already seen, or an older one, is ignored.
 *
 * <p>Once units are restocked or a replacement's Reservation committed, nothing is undone: every
 * later step is retried until it succeeds. A Refund the gateway declines, or one that can't be
 * made, holds the RMA instead; one that failed is first checked against the Payment, which an
 * attempt whose answer was lost may have refunded.
 */
public class ReturnsSaga implements ReturnsWorkflow {

  private static final Logger log = Workflow.getLogger(ReturnsSaga.class);

  /** The RMA's statuses on its way to an Inspection, in order. */
  private static final List<String> TO_INSPECTION = List.of("APPROVED", "RECEIVED", "INSPECTED");

  /** The RMA's final statuses once approved, and how a run that finds one ends. */
  private static final Map<String, Status> ENDED =
      Map.of(
          "EXPIRED", Status.EXPIRED,
          "REFUNDED", Status.REFUNDED,
          "EXCHANGED", Status.EXCHANGED,
          "CLOSED", Status.CLOSED,
          "ON_HOLD", Status.REFUND_REFUSED);

  /** A replacement's statuses on its way to the Customer, in order. */
  private static final List<String> ON_ITS_WAY =
      List.of("AWAITING_PICK", "PICKED", "PACKED", "SHIPPED", "DELIVERED");

  /** The note an RMA is refunded with when a confirmed fault can't be replaced. */
  static final String NO_REPLACEMENT = "No replacement in Stock";

  /** Refunding: a gateway failure is retried for about a minute, then taken as a refusal. */
  private static final ActivityOptions FOR_A_MINUTE =
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(CheckoutSaga.ATTEMPT)
          .setScheduleToCloseTimeout(Duration.ofMinutes(1))
          .setRetryOptions(
              RetryOptions.newBuilder()
                  .setInitialInterval(Duration.ofSeconds(1))
                  .setBackoffCoefficient(2)
                  .setMaximumInterval(Duration.ofSeconds(10))
                  .setDoNotRetry(Refusals.REFUSED)
                  .build())
          .build();

  /**
   * Reading the RMA, a replacement or the Payment again, and marking the Order returned: retried
   * until answered, but not when refused.
   */
  private static final ActivityOptions UNTIL_ANSWERED =
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(CheckoutSaga.ATTEMPT)
          .setRetryOptions(
              RetryOptions.newBuilder()
                  .setInitialInterval(Duration.ofSeconds(1))
                  .setBackoffCoefficient(2)
                  .setMaximumInterval(Duration.ofMinutes(1))
                  .setDoNotRetry(Refusals.REFUSED)
                  .build())
          .build();

  /** Every other step: retried until it succeeds, refusals too. */
  private static final ActivityOptions UNTIL_DONE =
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(CheckoutSaga.ATTEMPT)
          .setRetryOptions(
              RetryOptions.newBuilder()
                  .setInitialInterval(Duration.ofSeconds(1))
                  .setBackoffCoefficient(2)
                  .setMaximumInterval(Duration.ofMinutes(1))
                  .build())
          .build();

  private final RmaActivities rmas = Workflow.newActivityStub(RmaActivities.class, UNTIL_DONE);
  private final RmaActivities rmasUntilAnswered =
      Workflow.newActivityStub(RmaActivities.class, UNTIL_ANSWERED);
  private final ReturnsInventoryActivities inventory =
      Workflow.newActivityStub(ReturnsInventoryActivities.class, UNTIL_DONE);
  private final RefundActivities refunds =
      Workflow.newActivityStub(RefundActivities.class, FOR_A_MINUTE);
  private final RefundActivities refundsUntilAnswered =
      Workflow.newActivityStub(RefundActivities.class, UNTIL_ANSWERED);
  private final ReplacementActivities replacements =
      Workflow.newActivityStub(ReplacementActivities.class, UNTIL_DONE);
  private final ReplacementActivities replacementsUntilAnswered =
      Workflow.newActivityStub(ReplacementActivities.class, UNTIL_ANSWERED);
  private final OrderReturnActivities orders =
      Workflow.newActivityStub(OrderReturnActivities.class, UNTIL_ANSWERED);

  /** The newest RMA event seen: its version, and the RMA's status at it. */
  private long rmaVersion;

  private String rmaStatus;

  /** The newest replacement Shipment event seen: its version, and its status at it. */
  private long shipmentVersion;

  private String shipmentStatus;

  @Override
  public void rmaChanged(RmaChange change) {
    if (change.version() <= rmaVersion) {
      log.info("Ignored RMA version {}: version {} was seen already", change.version(), rmaVersion);
      return;
    }
    rmaVersion = change.version();
    // An RMA never goes back, but a read may have seen further than a late event says.
    foundRma(change.status());
  }

  @Override
  public void shipmentChanged(ShipmentChange change) {
    if (change.version() <= shipmentVersion) {
      log.info(
          "Ignored replacement Shipment {} version {}: version {} was seen already",
          change.shipmentId(),
          change.version(),
          shipmentVersion);
      return;
    }
    shipmentVersion = change.version();
    shipmentStatus = change.status();
  }

  @Override
  public ReturnsOutcome resolve(ReturnsRequest request) {
    // Every activity's calls and log lines carry it from here (CorrelationIdPropagator).
    MDC.put("correlationId", request.correlationId());
    var rmaId = request.rmaId();
    var reference = request.reference();

    var rma = rmas.findRma(rmaId);
    var ended = ENDED.get(rma.status());
    if (ended != null) {
      // A run that Temporal no longer keeps took it this far, and counted it.
      log.info("{} is {} already: nothing more to do", reference, rma.status());
      return new ReturnsOutcome(ended, null, null, null);
    }
    foundRma(rma.status());

    if (!reachedRma("RECEIVED")) {
      var deadline =
          rma.sendBackBy() != null ? Instant.parse(rma.sendBackBy()) : request.sendBackBy();
      log.info("{} awaits its units until {}", reference, deadline);
      if (!awaitReceipt(deadline)) {
        if (rmas.expireRma(rmaId) == Expiry.EXPIRED) {
          log.info("{} expired: its units didn't come by {}", reference, deadline);
          return ended(new ReturnsOutcome(Status.EXPIRED, null, null, null));
        }
        log.info("{}'s units were received as its Send-back deadline passed: going on", reference);
        foundRma("RECEIVED");
      }
    }

    log.info("{} awaits its Inspection", reference);
    awaitInspection(rmaId, request.inspectionCheck());
    var inspected = inspectionOf(rmaId, request.inspectionCheck());
    var note = inspected.inspectionNote();
    return switch (inspected.inspectionOutcome()) {
      case "ACCEPTED" -> {
        inventory.restock(request.variantId(), request.quantity(), reference);
        yield refund(request, inspected, null);
      }
      case "FAULT_CONFIRMED" -> replace(request, inspected);
      default -> {
        log.info("{}'s Inspection refused it ({}): closing it", reference, note);
        rmas.resolveRma(rmaId, "CLOSED", null, null, note);
        yield ended(new ReturnsOutcome(Status.CLOSED, null, null, null));
      }
    };
  }

  /**
   * Awaits the units until {@code deadline}: whether they were received by then. With no deadline,
   * which an approved RMA always has, they are awaited for good.
   */
  private boolean awaitReceipt(Instant deadline) {
    if (deadline == null) {
      Workflow.await(() -> reachedRma("RECEIVED"));
      return true;
    }
    var left = Duration.ofMillis(deadline.toEpochMilli() - Workflow.currentTimeMillis());
    if (left.isNegative() || left.isZero()) {
      return reachedRma("RECEIVED");
    }
    return Workflow.await(left, () -> reachedRma("RECEIVED"));
  }

  /**
   * Awaits the Inspection, with no deadline: inspecting is Staff's work. Each time {@code
   * checkEvery} passes without an INSPECTED event, the RMA is read again, so a run whose event
   * never comes, dead-lettered say, still goes on once it is inspected.
   */
  private void awaitInspection(String rmaId, Duration checkEvery) {
    while (!Workflow.await(checkEvery, () -> reachedRma("INSPECTED"))) {
      try {
        foundRma(rmasUntilAnswered.findRma(rmaId).status());
      } catch (ActivityFailure e) {
        log.warn("RMA {} couldn't be read; still awaiting its Inspection", rmaId, e);
      }
    }
  }

  /**
   * The inspected RMA, with its Inspection. Returns & Warranty records both in one step, so a read
   * without one is read again after {@code checkEvery}, rather than failing the run.
   */
  private Rma inspectionOf(String rmaId, Duration checkEvery) {
    var rma = rmas.findRma(rmaId);
    while (rma.inspectionOutcome() == null) {
      log.warn(
          "RMA {} reads {} without an Inspection; reading it again later", rmaId, rma.status());
      Workflow.sleep(checkEvery);
      rma = rmas.findRma(rmaId);
    }
    return rma;
  }

  /**
   * A confirmed fault: reserves the replacement as {@code rma:<rmaId>} and commits it, creates the
   * replacement Shipment, awaits its packing, dispatches it and resolves the RMA {@code EXCHANGED}.
   * With too little Stock it is refunded instead, without restocking.
   */
  private ReturnsOutcome replace(ReturnsRequest request, Rma inspected) {
    var rmaId = request.rmaId();
    while (true) {
      var reservation =
          inventory.reserveReplacement(rmaId, request.variantId(), request.quantity());
      if (reservation == null) {
        log.info("{} has no replacement in Stock: refunding it", request.reference());
        return refund(request, inspected, NO_REPLACEMENT);
      }
      if (inventory.commitReplacement(rmaId, reservation)) {
        break;
      }
      log.warn(
          "{}'s Reservation {} lapsed before it was committed", request.reference(), reservation);
    }

    var replacement =
        replacements.createReplacement(
            rmaId,
            request.orderId(),
            request.customerId(),
            request.variantId(),
            request.quantity());
    foundShipment(replacement.status());
    if (reachedShipment("SHIPPED")) {
      log.info(
          "{}'s replacement {} is {} already",
          request.reference(),
          replacement.id(),
          shipmentStatus);
    } else {
      log.info("{}'s replacement {} awaits packing", request.reference(), replacement.id());
      awaitPacking(rmaId, request.packingCheck());
      replacements.dispatchReplacement(replacement.id());
    }
    rmas.resolveRma(rmaId, "EXCHANGED", null, replacement.id(), null);
    return ended(new ReturnsOutcome(Status.EXCHANGED, null, replacement.id(), null));
  }

  /** Awaits the replacement's packing as {@link #awaitInspection} awaits the Inspection. */
  private void awaitPacking(String rmaId, Duration checkEvery) {
    while (!Workflow.await(checkEvery, () -> reachedShipment("PACKED"))) {
      try {
        foundShipment(replacementsUntilAnswered.findReplacement(rmaId).status());
      } catch (ActivityFailure e) {
        log.warn("RMA {}'s replacement couldn't be read; still awaiting packing", rmaId, e);
      }
    }
  }

  /** Why the RMA is put on hold: its kind, one of {@link #HOLDS}, and the reason given. */
  private record Hold(String kind, String reason) {}

  /**
   * Refunds the RMA's Refund amount, as it is: what refunding all its units gives back. Then
   * resolves it {@code REFUNDED} with the Refund's reference, and marks the Order returned once
   * every unit it delivered is refunded. A Refund refused holds the RMA instead.
   */
  private ReturnsOutcome refund(ReturnsRequest request, Rma inspected, String note) {
    var rmaId = request.rmaId();
    var amount = refundAmountOf(request, inspected);
    String transaction;
    PaymentToRefund payment;
    try {
      payment =
          refunds.findRefundablePayment(
              request.customerId(), request.orderId(), request.reference());
    } catch (ActivityFailure e) {
      log.error("Order {}'s Payment to refund couldn't be found", request.orderId(), e);
      return held(
          rmaId, new Hold(REFUND_FAILED, "Refund failed: the Payment couldn't be refunded"));
    }
    if (payment.refundReference() != null) {
      transaction = payment.refundReference();
      log.info("{} was refunded already, as {}", request.reference(), transaction);
    } else {
      try {
        var refund =
            refunds.refundPayment(request.customerId(), payment.id(), amount, request.reference());
        if (refund.status() == Refund.Status.DECLINED) {
          log.info("{}'s Refund was declined: {}", request.reference(), refund.declineReason());
          return held(
              rmaId, new Hold(REFUND_DECLINED, "Refund declined: " + refund.declineReason()));
        }
        transaction = refund.reference();
      } catch (ActivityFailure e) {
        transaction = refundedAfterAll(request);
        if (transaction == null) {
          log.error("{}'s Refund couldn't be made", request.reference(), e);
          return held(
              rmaId, new Hold(REFUND_FAILED, "Refund failed: the Payment couldn't be refunded"));
        }
        log.warn("{}'s Refund failed, but it was made: going on", request.reference(), e);
      }
    }

    var orderFullyRefunded = rmas.resolveRma(rmaId, "REFUNDED", transaction, null, note);
    if (orderFullyRefunded) {
      markReturned(request.customerId(), request.orderId());
    }
    return ended(new ReturnsOutcome(Status.REFUNDED, transaction, null, null));
  }

  /**
   * The RMA's Refund amount: the Inspection's read has it, or the event that started the run. An
   * RMA from Sprint 5 gains one once its Order is projected, so until then it is read again each
   * hour.
   */
  private Amount refundAmountOf(ReturnsRequest request, Rma inspected) {
    var amount =
        inspected.refundAmount() != null ? inspected.refundAmount() : request.refundAmount();
    while (amount == null) {
      log.warn("{} has no Refund amount yet: reading it again later", request.reference());
      Workflow.sleep(request.inspectionCheck());
      try {
        amount = rmasUntilAnswered.findRma(request.rmaId()).refundAmount();
      } catch (ActivityFailure e) {
        log.warn("RMA {} couldn't be read; still awaiting its Refund amount", request.rmaId(), e);
      }
    }
    return amount;
  }

  /**
   * The reference of a Refund made for the RMA after all, read again once refunding failed: an
   * attempt that timed out may have been made, so the hold, which is final, waits for Payment's
   * answer. Null when none was.
   */
  private String refundedAfterAll(ReturnsRequest request) {
    try {
      return refundsUntilAnswered
          .findRefundablePayment(request.customerId(), request.orderId(), request.reference())
          .refundReference();
    } catch (ActivityFailure e) {
      return null;
    }
  }

  private ReturnsOutcome held(String rmaId, Hold hold) {
    count("returns_holds", "kind", hold.kind());
    rmas.holdRma(rmaId, hold.reason());
    return ended(new ReturnsOutcome(Status.REFUND_REFUSED, null, null, hold.reason()));
  }

  /**
   * Marks the Order returned, once its every delivered unit is refunded. Order Management refuses
   * it for an Order that isn't delivered, or is returned already; that is logged, and changes
   * nothing here.
   */
  private void markReturned(String customerId, String orderId) {
    try {
      orders.markOrderReturned(customerId, orderId);
    } catch (ActivityFailure e) {
      log.warn("Order {} couldn't be marked returned", orderId, e);
    }
  }

  /**
   * Takes the RMA's status as read from Returns & Warranty, which carries no version: it counts
   * only when it has come further than the events seen so far.
   */
  private void foundRma(String status) {
    if (rmaStatus == null || TO_INSPECTION.indexOf(status) > TO_INSPECTION.indexOf(rmaStatus)) {
      rmaStatus = status;
    }
  }

  private boolean reachedRma(String status) {
    return rmaStatus != null && TO_INSPECTION.indexOf(rmaStatus) >= TO_INSPECTION.indexOf(status);
  }

  /** As {@link #foundRma}, for the replacement Shipment. */
  private void foundShipment(String status) {
    if (shipmentStatus == null || ON_ITS_WAY.indexOf(status) > ON_ITS_WAY.indexOf(shipmentStatus)) {
      shipmentStatus = status;
    }
  }

  private boolean reachedShipment(String status) {
    return shipmentStatus != null
        && ON_ITS_WAY.indexOf(shipmentStatus) >= ON_ITS_WAY.indexOf(status);
  }

  private static ReturnsOutcome ended(ReturnsOutcome outcome) {
    count("returns_workflows", "outcome", outcome.status().name());
    return outcome;
  }

  /** Counts once per run, however often it replays: Temporal's scope skips replays. */
  private static void count(String counter, String tag, String value) {
    Workflow.getMetricsScope().tagged(Map.of(tag, value)).counter(counter).inc(1);
  }
}
