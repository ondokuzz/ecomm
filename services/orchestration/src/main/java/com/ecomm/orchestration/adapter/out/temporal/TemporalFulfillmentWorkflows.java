package com.ecomm.orchestration.adapter.out.temporal;

import com.ecomm.orchestration.application.port.in.FulfillmentRequest;
import com.ecomm.orchestration.application.port.in.FulfillmentWorkflow;
import com.ecomm.orchestration.application.port.in.ShipmentChange;
import com.ecomm.orchestration.application.port.out.FulfillmentWorkflows;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import org.springframework.stereotype.Component;

/**
 * Starts each Order's fulfillment as {@code fulfillment:<orderId>}, and signals it there. The ID's
 * reuse policy refuses any second start, even once the first run has closed, for as long as
 * Temporal keeps the closed run (the namespace's retention).
 */
@Component
class TemporalFulfillmentWorkflows implements FulfillmentWorkflows {

  private final WorkflowClient temporal;

  TemporalFulfillmentWorkflows(WorkflowClient temporal) {
    this.temporal = temporal;
  }

  @Override
  public boolean start(FulfillmentRequest request) {
    var workflow =
        temporal.newWorkflowStub(
            FulfillmentWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(FulfillmentWorkflow.TASK_QUEUE)
                .setWorkflowId(FulfillmentWorkflow.idOf(request.orderId()))
                .setWorkflowIdReusePolicy(
                    WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .build());
    try {
      WorkflowClient.start(workflow::fulfil, request);
      return true;
    } catch (WorkflowExecutionAlreadyStarted e) {
      return false;
    }
  }

  @Override
  public boolean signal(String orderId, ShipmentChange change) {
    var workflow =
        temporal.newWorkflowStub(FulfillmentWorkflow.class, FulfillmentWorkflow.idOf(orderId));
    try {
      workflow.shipmentChanged(change);
      return true;
    } catch (WorkflowNotFoundException e) {
      return false;
    }
  }
}
