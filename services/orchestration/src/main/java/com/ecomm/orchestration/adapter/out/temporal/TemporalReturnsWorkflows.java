package com.ecomm.orchestration.adapter.out.temporal;

import com.ecomm.orchestration.application.port.in.ReturnsRequest;
import com.ecomm.orchestration.application.port.in.ReturnsWorkflow;
import com.ecomm.orchestration.application.port.in.RmaChange;
import com.ecomm.orchestration.application.port.in.ShipmentChange;
import com.ecomm.orchestration.application.port.out.ReturnsWorkflows;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Starts each RMA's returns Saga as {@code returns:<rmaId>}, and signals it there. As for
 * fulfillment, the ID's reuse policy refuses any second start, even once the first run has closed,
 * for as long as Temporal keeps the closed run (the namespace's retention).
 */
@Component
class TemporalReturnsWorkflows implements ReturnsWorkflows {

  private final WorkflowClient temporal;

  TemporalReturnsWorkflows(WorkflowClient temporal) {
    this.temporal = temporal;
  }

  @Override
  public boolean start(ReturnsRequest request) {
    var workflow =
        temporal.newWorkflowStub(
            ReturnsWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(ReturnsWorkflow.TASK_QUEUE)
                .setWorkflowId(ReturnsWorkflow.idOf(request.rmaId()))
                .setWorkflowIdReusePolicy(
                    WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .build());
    try {
      WorkflowClient.start(workflow::resolve, request);
      return true;
    } catch (WorkflowExecutionAlreadyStarted e) {
      return false;
    }
  }

  @Override
  public boolean signal(String rmaId, RmaChange change) {
    return signal(rmaId, workflow -> workflow.rmaChanged(change));
  }

  @Override
  public boolean signal(String rmaId, ShipmentChange change) {
    return signal(rmaId, workflow -> workflow.shipmentChanged(change));
  }

  private boolean signal(String rmaId, Consumer<ReturnsWorkflow> signal) {
    try {
      signal.accept(temporal.newWorkflowStub(ReturnsWorkflow.class, ReturnsWorkflow.idOf(rmaId)));
      return true;
    } catch (WorkflowNotFoundException e) {
      return false;
    }
  }
}
