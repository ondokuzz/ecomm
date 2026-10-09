package com.ecomm.orchestration.application;

import com.ecomm.orchestration.application.port.in.CheckoutOutcome;
import com.ecomm.orchestration.application.port.in.CheckoutOutcome.Status;
import com.ecomm.orchestration.application.port.in.CheckoutRequest;
import com.ecomm.orchestration.application.port.in.CheckoutWorkflow;
import com.ecomm.orchestration.application.port.out.CartActivities;
import com.ecomm.orchestration.application.port.out.CheckoutSessionActivities;
import com.ecomm.orchestration.application.port.out.InventoryActivities;
import com.ecomm.orchestration.application.port.out.InventoryActivities.Commitment;
import com.ecomm.orchestration.application.port.out.OrderActivities;
import com.ecomm.orchestration.application.port.out.OrderActivities.PlacedOrder;
import com.ecomm.orchestration.application.port.out.PaymentActivities;
import com.ecomm.orchestration.application.port.out.PaymentActivities.Authorization;
import com.ecomm.orchestration.application.port.out.Refusals;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.MDC;

/**
 * The checkout Saga's steps, in order. Every step is an activity, so this code only decides: it
 * does no I/O, reads no clock and keeps no state outside the workflow, and replays the same way
 * every time.
 *
 * <p>Up to the commit, a step that fails for good undoes what came before it: the Order is
 * cancelled, and an authorized Payment voided. Once the Stock is committed nothing is undone: the
 * Order is marked paid however long that takes, and the outcome is {@code PAID} from then on, kept
 * in the memo as {@value #OUTCOME_MEMO} while the Cart is cleared and the session ended.
 */
public class CheckoutSaga implements CheckoutWorkflow {

  /** The memo key under which a run keeps its outcome once it is {@code PAID}. */
  public static final String OUTCOME_MEMO = "outcome";

  private static final Logger log = Workflow.getLogger(CheckoutSaga.class);

  /** Placing the Order and committing its Stock: transient failures for up to about a minute. */
  private static final ActivityOptions TRANSIENT_FOR_A_MINUTE =
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(10))
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
   * Authorizing: a gateway failure is retried 3 times over about 10 seconds, with the same key, so
   * the Customer isn't kept waiting on a gateway that is down.
   */
  private static final ActivityOptions AUTHORIZING =
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(15))
          .setRetryOptions(
              RetryOptions.newBuilder()
                  .setInitialInterval(Duration.ofMillis(1500))
                  .setBackoffCoefficient(2)
                  .setMaximumAttempts(4)
                  .setDoNotRetry(Refusals.REFUSED)
                  .build())
          .build();

  /** Marking the Order paid and the compensations: retried until they succeed, refusals too. */
  private static final ActivityOptions UNTIL_DONE =
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(10))
          .setRetryOptions(
              RetryOptions.newBuilder()
                  .setInitialInterval(Duration.ofSeconds(1))
                  .setBackoffCoefficient(2)
                  .setMaximumInterval(Duration.ofMinutes(1))
                  .build())
          .build();

  /** Clearing the Cart and ending the session: retried for up to an hour, then given up. */
  private static final ActivityOptions FOR_AN_HOUR =
      ActivityOptions.newBuilder(UNTIL_DONE).setScheduleToCloseTimeout(Duration.ofHours(1)).build();

  private final OrderActivities ordersForAMinute =
      Workflow.newActivityStub(OrderActivities.class, TRANSIENT_FOR_A_MINUTE);
  private final OrderActivities ordersUntilDone =
      Workflow.newActivityStub(OrderActivities.class, UNTIL_DONE);
  private final PaymentActivities paymentsTriedFourTimes =
      Workflow.newActivityStub(PaymentActivities.class, AUTHORIZING);
  private final PaymentActivities paymentsUntilDone =
      Workflow.newActivityStub(PaymentActivities.class, UNTIL_DONE);
  private final InventoryActivities inventoryForAMinute =
      Workflow.newActivityStub(InventoryActivities.class, TRANSIENT_FOR_A_MINUTE);
  private final CartActivities cart = Workflow.newActivityStub(CartActivities.class, FOR_AN_HOUR);
  private final CheckoutSessionActivities sessions =
      Workflow.newActivityStub(CheckoutSessionActivities.class, FOR_AN_HOUR);

  @Override
  public CheckoutOutcome checkout(CheckoutRequest request) {
    // Every activity's calls and log lines carry it from here (CorrelationIdPropagator).
    MDC.put("correlationId", request.correlationId());
    var customerId = request.customerId();

    PlacedOrder order;
    try {
      order =
          ordersForAMinute.placeOrder(
              customerId, request.lines(), request.discounts(), request.tax());
    } catch (ActivityFailure e) {
      log.error("The Order couldn't be placed; nothing to undo", e);
      return ended(new CheckoutOutcome(Status.FAILED, null, null));
    }
    var orderId = order.orderId();

    Authorization authorization;
    try {
      authorization =
          paymentsTriedFourTimes.authorizePayment(
              customerId, orderId, order.total(), request.paymentMethod());
    } catch (ActivityFailure e) {
      log.error("Order {}'s Payment couldn't be authorized", orderId, e);
      cancelOrder(customerId, orderId);
      return ended(new CheckoutOutcome(Status.FAILED, orderId, null));
    }
    switch (authorization.status()) {
      case AUTHORIZED -> {}
      case DECLINED -> {
        log.info("Order {}'s Payment was declined: {}", orderId, authorization.declineReason());
        cancelOrder(customerId, orderId);
        return ended(new CheckoutOutcome(Status.DECLINED, orderId, authorization.declineReason()));
      }
      // Until the Saga awaits a bank's settlement (#61), a pending authorization is undone.
      case PENDING -> {
        log.warn("Order {}'s Payment is pending, which this Saga can't wait for yet", orderId);
        voidPayment(customerId, authorization.paymentId());
        cancelOrder(customerId, orderId);
        return ended(new CheckoutOutcome(Status.FAILED, orderId, null));
      }
    }

    Commitment commitment;
    try {
      commitment = inventoryForAMinute.commitReservation(customerId, request.reservationId());
    } catch (ActivityFailure e) {
      log.error("Order {}'s Reservation couldn't be committed", orderId, e);
      commitment = null;
    }
    if (commitment != Commitment.COMMITTED) {
      voidPayment(customerId, authorization.paymentId());
      cancelOrder(customerId, orderId);
      return ended(
          new CheckoutOutcome(
              commitment == Commitment.LAPSED ? Status.HOLD_EXPIRED : Status.FAILED,
              orderId,
              null));
    }

    ordersUntilDone.markOrderPaid(customerId, orderId);
    var paid = new CheckoutOutcome(Status.PAID, orderId, null);
    Workflow.upsertMemo(Map.of(OUTCOME_MEMO, paid));
    count("checkout_workflows", "outcome", Status.PAID.name());

    try {
      cart.clearCart(customerId);
    } catch (ActivityFailure e) {
      log.error(
          "Customer {}'s Cart wasn't cleared after Order {} was paid", customerId, orderId, e);
    }
    try {
      sessions.endCheckoutSession(customerId, request.checkoutSessionId());
    } catch (ActivityFailure e) {
      log.error(
          "Checkout Session {} wasn't ended after Order {} was paid",
          request.checkoutSessionId(),
          orderId,
          e);
    }
    return paid;
  }

  private void cancelOrder(String customerId, String orderId) {
    count("checkout_compensations", "kind", "cancelOrder");
    ordersUntilDone.cancelOrder(customerId, orderId);
  }

  private void voidPayment(String customerId, String paymentId) {
    count("checkout_compensations", "kind", "voidPayment");
    paymentsUntilDone.voidPayment(customerId, paymentId);
  }

  /** {@code outcome}, counted; every outcome but {@code PAID}, which is counted once decided. */
  private static CheckoutOutcome ended(CheckoutOutcome outcome) {
    count("checkout_workflows", "outcome", outcome.status().name());
    return outcome;
  }

  /** Counts once per run, however often it replays: Temporal's scope skips replays. */
  private static void count(String counter, String tag, String value) {
    Workflow.getMetricsScope().tagged(Map.of(tag, value)).counter(counter).inc(1);
  }
}
