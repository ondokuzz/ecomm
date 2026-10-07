package com.ecomm.orchestration.application.port.in;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * The checkout Saga (ADR 0009), which Checkout starts on the {@code checkout} task queue when a
 * Customer pays a Checkout Session.
 *
 * <p>For now a placeholder that proves the worker is registered; #60 gives it its steps.
 */
@WorkflowInterface
public interface CheckoutWorkflow {

  /** Checks out the Checkout Session {@code checkoutSessionId}; for now, answers with its ID. */
  @WorkflowMethod
  String checkout(String checkoutSessionId);
}
