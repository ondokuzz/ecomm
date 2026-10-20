package com.ecomm.orchestration.application.port.in;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.List;

/**
 * The returns Saga (ADR 0009), which Orchestration starts on the {@value #TASK_QUEUE} task queue as
 * the workflow type {@code returns} when an RMA is approved, with the ID {@code returns:<rmaId>},
 * and wakes with the RMA's events and its replacement Shipment's.
 */
@WorkflowInterface
public interface ReturnsWorkflow {

  String TASK_QUEUE = "returns";

  /** A hold for a Refund the gateway declined. */
  String REFUND_DECLINED = "refundDeclined";

  /** A hold for a Refund that couldn't be made: no captured Payment, or Payment down. */
  String REFUND_FAILED = "refundFailed";

  /** Every kind of hold, as {@code returns_holds} counts them. */
  List<String> HOLDS = List.of(REFUND_DECLINED, REFUND_FAILED);

  /** The workflow ID of the RMA's returns Saga, which Temporal's UI finds it by. */
  static String idOf(String rmaId) {
    return "returns:" + rmaId;
  }

  /**
   * Awaits the units until the Send-back deadline, expiring the RMA if they don't come; awaits
   * their Inspection; then restocks and refunds an accepted return, replaces a confirmed fault (or
   * refunds it with no Stock), or closes a refused one, and marks the Order returned once every
   * unit it delivered is refunded.
   */
  @WorkflowMethod(name = "returns")
  ReturnsOutcome resolve(ReturnsRequest request);

  /** An RMA event; one at a version already seen, or an older one, is ignored. */
  @SignalMethod(name = "rmaChanged")
  void rmaChanged(RmaChange change);

  /** A replacement Shipment event; one at a version already seen, or older, is ignored. */
  @SignalMethod(name = "shipmentChanged")
  void shipmentChanged(ShipmentChange change);
}
