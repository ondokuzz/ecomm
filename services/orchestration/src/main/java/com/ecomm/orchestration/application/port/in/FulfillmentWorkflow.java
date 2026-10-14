package com.ecomm.orchestration.application.port.in;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.List;

/**
 * The fulfillment Saga (ADR 0009), which Orchestration starts on the {@value #TASK_QUEUE} task
 * queue as the workflow type {@code fulfillment} when an Order is paid, with the ID {@code
 * fulfillment:<orderId>}, and wakes with the Order's Shipment events.
 */
@WorkflowInterface
public interface FulfillmentWorkflow {

  String TASK_QUEUE = "fulfillment";

  /** A hold for a capture the gateway declined. */
  String CAPTURE_DECLINED = "captureDeclined";

  /** A hold for a capture that couldn't be made: no Payment to capture, or the gateway down. */
  String CAPTURE_FAILED = "captureFailed";

  /** Every kind of hold, as {@code fulfillment_holds} counts them. */
  List<String> HOLDS = List.of(CAPTURE_DECLINED, CAPTURE_FAILED);

  /** The workflow ID of the Order's fulfillment, which Temporal's UI finds it by. */
  static String idOf(String orderId) {
    return "fulfillment:" + orderId;
  }

  /**
   * Creates the Shipment, awaits its packing, captures the Payment, marks the Order fulfilled,
   * dispatches the Shipment, marks the Order shipped, awaits delivery and marks the Order
   * delivered. A refused capture puts the Shipment on hold instead.
   */
  @WorkflowMethod(name = "fulfillment")
  FulfillmentOutcome fulfil(FulfillmentRequest request);

  /** A Shipment event; one at a version already seen, or an older one, is ignored. */
  @SignalMethod(name = "shipmentChanged")
  void shipmentChanged(ShipmentChange change);
}
