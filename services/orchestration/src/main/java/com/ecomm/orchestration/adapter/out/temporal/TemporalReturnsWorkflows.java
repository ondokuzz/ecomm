package com.ecomm.orchestration.adapter.out.temporal;

import com.ecomm.orchestration.application.port.in.ShipmentChange;
import com.ecomm.orchestration.application.port.out.ReturnsWorkflows;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Signals each RMA's returns Saga at {@code returns:<rmaId>}, with {@value #SHIPMENT_CHANGED}. The
 * Saga's workflow interface, when it exists, declares that signal under the same name.
 */
@Component
class TemporalReturnsWorkflows implements ReturnsWorkflows {

  /** The returns Saga's signal for its replacement Shipment's events. */
  static final String SHIPMENT_CHANGED = "shipmentChanged";

  private final WorkflowClient temporal;

  TemporalReturnsWorkflows(WorkflowClient temporal) {
    this.temporal = temporal;
  }

  @Override
  public boolean signal(String rmaId, ShipmentChange change) {
    try {
      temporal.newUntypedWorkflowStub("returns:" + rmaId).signal(SHIPMENT_CHANGED, change);
      return true;
    } catch (WorkflowNotFoundException e) {
      return false;
    }
  }
}
