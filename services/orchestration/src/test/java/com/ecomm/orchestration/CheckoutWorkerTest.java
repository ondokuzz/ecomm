package com.ecomm.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.orchestration.application.port.in.CheckoutWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Orchestration is the worker on the {@code checkout} task queue. */
class CheckoutWorkerTest extends OrchestrationTest {

  @Autowired WorkflowClient temporal;

  @Test
  void aCheckoutWorkflowStartedOnTheCheckoutQueueCompletes() {
    var workflow =
        temporal.newWorkflowStub(
            CheckoutWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("checkout")
                .setWorkflowId("checkout-session-1")
                .build());

    assertThat(workflow.checkout("session-1")).isEqualTo("session-1");
  }
}
