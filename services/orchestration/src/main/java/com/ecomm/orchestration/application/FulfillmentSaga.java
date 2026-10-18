package com.ecomm.orchestration.application;

import com.ecomm.orchestration.application.port.in.FulfillmentOutcome;
import com.ecomm.orchestration.application.port.in.FulfillmentOutcome.Status;
import com.ecomm.orchestration.application.port.in.FulfillmentRequest;
import com.ecomm.orchestration.application.port.in.FulfillmentWorkflow;
import com.ecomm.orchestration.application.port.in.ShipmentChange;
import com.ecomm.orchestration.application.port.out.CaptureActivities;
import com.ecomm.orchestration.application.port.out.CaptureActivities.Capture;
import com.ecomm.orchestration.application.port.out.CaptureActivities.PaymentToCapture;
import com.ecomm.orchestration.application.port.out.OrderFulfillmentActivities;
import com.ecomm.orchestration.application.port.out.Refusals;
import com.ecomm.orchestration.application.port.out.ShipmentActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.MDC;

/**
 * The fulfillment Saga's steps, in order. As in the checkout Saga, every step is an activity, so
 * this code only decides, and replays the same way every time.
 *
 * <p>It waits twice on the Order's Shipment, woken by its events: for Staff to pack it, with no
 * deadline, and for the Carrier to deliver it, for the request's delivery deadline. An event at a
 * version it has already seen, or an older one, is ignored.
 *
 * <p>Once the Payment is captured nothing is undone: every later step is retried until it succeeds.
 * A capture the gateway declines, or one that can't be made, puts the Shipment on hold instead and
 * leaves the Order {@code PAID}; one that failed is first checked against the Payment, which an
 * attempt whose answer was lost may have captured.
 */
public class FulfillmentSaga implements FulfillmentWorkflow {

  private static final Logger log = Workflow.getLogger(FulfillmentSaga.class);

  /** The Shipment's statuses on its way to the Customer, in order. */
  private static final List<String> ON_ITS_WAY =
      List.of("AWAITING_PICK", "PICKED", "PACKED", "SHIPPED", "DELIVERED");

  /** Capturing: a gateway failure is retried for about a minute, then taken as a refusal. */
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
   * Creating the Shipment, reading the Payment again once its capture failed, and marking delivered
   * the Order of a Shipment an earlier run dispatched: retried until it succeeds, such as while
   * Fulfillment & Shipping waits for Catalog's Products, but not when it is refused.
   */
  private static final ActivityOptions UNTIL_CREATED =
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

  /** Every step after the capture, and the hold: retried until they succeed, refusals too. */
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

  private final ShipmentActivities shipmentsUntilCreated =
      Workflow.newActivityStub(ShipmentActivities.class, UNTIL_CREATED);
  private final ShipmentActivities shipmentsUntilDone =
      Workflow.newActivityStub(ShipmentActivities.class, UNTIL_DONE);
  private final CaptureActivities captures =
      Workflow.newActivityStub(CaptureActivities.class, FOR_A_MINUTE);
  private final CaptureActivities capturesUntilAnswered =
      Workflow.newActivityStub(CaptureActivities.class, UNTIL_CREATED);
  private final OrderFulfillmentActivities orders =
      Workflow.newActivityStub(OrderFulfillmentActivities.class, UNTIL_DONE);
  private final OrderFulfillmentActivities ordersUntilAnswered =
      Workflow.newActivityStub(OrderFulfillmentActivities.class, UNTIL_CREATED);

  /** The newest Shipment event seen: its version, and the Shipment's status at it. */
  private long shipmentVersion;

  private String shipmentStatus;

  @Override
  public void shipmentChanged(ShipmentChange change) {
    if (change.version() <= shipmentVersion) {
      log.info(
          "Ignored Shipment {} version {}: version {} was seen already",
          change.shipmentId(),
          change.version(),
          shipmentVersion);
      return;
    }
    shipmentVersion = change.version();
    shipmentStatus = change.status();
  }

  @Override
  public FulfillmentOutcome fulfil(FulfillmentRequest request) {
    // Every activity's calls and log lines carry it from here (CorrelationIdPropagator).
    MDC.put("correlationId", request.correlationId());
    var customerId = request.customerId();
    var orderId = request.orderId();

    ShipmentActivities.Shipment shipment;
    try {
      shipment =
          shipmentsUntilCreated.createShipment(
              orderId,
              customerId,
              request.lines(),
              request.shippingAddress(),
              request.shippingOption());
    } catch (ActivityFailure e) {
      log.error("Order {}'s Shipment couldn't be created; nothing was charged", orderId, e);
      return ended(new FulfillmentOutcome(Status.FAILED, null, null));
    }
    var shipmentId = shipment.id();
    if ("ON_HOLD".equals(shipment.status())) {
      log.info(
          "Order {}'s Shipment {} is on hold already: {}",
          orderId,
          shipmentId,
          shipment.holdReason());
      return ended(
          new FulfillmentOutcome(Status.CAPTURE_REFUSED, shipmentId, shipment.holdReason()));
    }
    found(shipment.status());
    var shippedBefore = reached("SHIPPED");
    if (shippedBefore) {
      // Dispatched by an earlier run, once it had captured the Payment and marked the Order
      // fulfilled, and that run marked it shipped: only delivery is left.
      log.info("Order {}'s Shipment {} is {} already", orderId, shipmentId, shipmentStatus);
    } else {
      log.info("Order {}'s Shipment {} awaits packing", orderId, shipmentId);
      Workflow.await(() -> reached("PACKED"));

      var hold = capture(customerId, orderId);
      if (hold != null) {
        count("fulfillment_holds", "kind", hold.kind());
        shipmentsUntilDone.holdShipment(shipmentId, hold.reason());
        return ended(new FulfillmentOutcome(Status.CAPTURE_REFUSED, shipmentId, hold.reason()));
      }

      orders.markOrderFulfilled(customerId, orderId);
      shipmentsUntilDone.dispatchShipment(shipmentId);
      orders.markOrderShipped(customerId, orderId);
    }

    if (!Workflow.await(request.deliveryDeadline(), () -> reached("DELIVERED"))) {
      log.warn(
          "Order {}'s Shipment {} wasn't confirmed delivered within {}",
          orderId,
          shipmentId,
          request.deliveryDeadline());
      return ended(new FulfillmentOutcome(Status.DELIVERY_UNCONFIRMED, shipmentId, null));
    }
    if (shippedBefore) {
      markDeliveredUnlessItIs(customerId, orderId);
    } else {
      orders.markOrderDelivered(customerId, orderId);
    }
    return ended(new FulfillmentOutcome(Status.DELIVERED, shipmentId, null));
  }

  /**
   * Marks delivered the Order of a Shipment an earlier run dispatched. That run may have marked it
   * delivered already, so Order Management's refusal to deliver it twice counts as done.
   */
  private void markDeliveredUnlessItIs(String customerId, String orderId) {
    try {
      ordersUntilAnswered.markOrderDelivered(customerId, orderId);
    } catch (ActivityFailure e) {
      log.info("Order {} was marked delivered already, as its refusal says", orderId, e);
    }
  }

  /** Why the Shipment is put on hold: its kind, one of {@link #HOLDS}, and the reason given. */
  private record Hold(String kind, String reason) {}

  /** Captures the Order's Payment: null once captured, else why the Shipment is held. */
  private Hold capture(String customerId, String orderId) {
    PaymentToCapture payment;
    try {
      payment = captures.findPayment(customerId, orderId);
    } catch (ActivityFailure e) {
      log.error("Order {}'s Payment to capture couldn't be found", orderId, e);
      return new Hold(CAPTURE_FAILED, "Capture failed: the Payment couldn't be captured");
    }
    Capture capture;
    try {
      capture = captures.capturePayment(customerId, payment.id());
    } catch (ActivityFailure e) {
      if (capturedAfterAll(customerId, orderId)) {
        log.warn("Order {}'s capture failed, but its Payment is captured: going on", orderId, e);
        return null;
      }
      log.error("Order {}'s Payment couldn't be captured", orderId, e);
      return new Hold(CAPTURE_FAILED, "Capture failed: the Payment couldn't be captured");
    }
    if (capture.status() == Capture.Status.DECLINED) {
      log.info("Order {}'s capture was declined: {}", orderId, capture.declineReason());
      return new Hold(CAPTURE_DECLINED, "Capture declined: " + capture.declineReason());
    }
    return null;
  }

  /**
   * Whether the Order's Payment is captured after all, read again once capturing it failed: an
   * attempt that timed out may have been made, so the hold, which is final, waits for Payment's
   * answer. With no Payment to capture, it isn't.
   */
  private boolean capturedAfterAll(String customerId, String orderId) {
    try {
      return capturesUntilAnswered.findPayment(customerId, orderId).captured();
    } catch (ActivityFailure e) {
      return false;
    }
  }

  /**
   * Takes the Shipment's status as read from Fulfillment & Shipping, which carries no version: it
   * counts only when it has come further than the events seen so far, so a read that crossed a
   * newer event never takes the Saga back.
   */
  private void found(String status) {
    if (shipmentStatus == null || ON_ITS_WAY.indexOf(status) > ON_ITS_WAY.indexOf(shipmentStatus)) {
      shipmentStatus = status;
    }
  }

  /** Whether the Shipment has come as far as {@code status} on its way to the Customer. */
  private boolean reached(String status) {
    return shipmentStatus != null
        && ON_ITS_WAY.indexOf(shipmentStatus) >= ON_ITS_WAY.indexOf(status);
  }

  private static FulfillmentOutcome ended(FulfillmentOutcome outcome) {
    count("fulfillment_workflows", "outcome", outcome.status().name());
    return outcome;
  }

  /** Counts once per run, however often it replays: Temporal's scope skips replays. */
  private static void count(String counter, String tag, String value) {
    Workflow.getMetricsScope().tagged(Map.of(tag, value)).counter(counter).inc(1);
  }
}
