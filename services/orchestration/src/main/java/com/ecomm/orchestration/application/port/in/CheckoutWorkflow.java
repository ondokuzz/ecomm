package com.ecomm.orchestration.application.port.in;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * The checkout Saga (ADR 0009), which Checkout starts on the {@code checkout} task queue as the
 * workflow type {@code checkout} when a Customer pays a Checkout Session, with the session's ID as
 * the workflow's.
 */
@WorkflowInterface
public interface CheckoutWorkflow {

  /**
   * Places the Order, authorizes its Payment, commits the Reservation, marks the Order paid, clears
   * the Cart and ends the session, undoing what it must when a step can't go on.
   */
  @WorkflowMethod(name = "checkout")
  CheckoutOutcome checkout(CheckoutRequest request);
}
