package com.ecomm.checkoutpricing.adapter.out.temporal;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUnavailableException;
import com.ecomm.checkoutpricing.application.port.out.CheckoutSaga;
import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.checkoutpricing.domain.PaymentAttempt;
import com.ecomm.checkoutpricing.domain.PaymentAttempt.Status;
import com.ecomm.commons.money.Money;
import com.ecomm.commons.web.CorrelationId;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The checkout Saga on Temporal: the workflow type {@code checkout} on the task queue {@code
 * checkout}, which Orchestration runs. Its workflow ID is the Checkout Session's, so starting one
 * while it runs joins it, and starting one after it closed starts a new run. Its input and outcome
 * are JSON in the shapes Orchestration's {@code CheckoutRequest} and {@code CheckoutOutcome} have;
 * the two services share no code.
 *
 * <p>The memo carries what the outcome alone doesn't: the Customer who paid, so only they read it,
 * and, once the Order is paid, the {@code PAID} outcome, while the run still clears the Cart and
 * ends the session.
 */
@Component
class TemporalCheckoutSaga implements CheckoutSaga {

  static final String WORKFLOW_TYPE = "checkout";
  static final String TASK_QUEUE = "checkout";
  static final String CUSTOMER_MEMO = "customerId";
  static final String OUTCOME_MEMO = "outcome";

  private static final Logger log = LoggerFactory.getLogger(TemporalCheckoutSaga.class);

  private final WorkflowClient temporal;
  private final Duration wait;

  TemporalCheckoutSaga(
      WorkflowClient temporal, @Value("${ecomm.checkout.saga.wait}") Duration wait) {
    this.temporal = temporal;
    this.wait = wait;
  }

  /** The workflow's input, as Orchestration reads it. */
  record Request(
      String checkoutSessionId,
      String customerId,
      List<Line> lines,
      List<DiscountBody> discounts,
      Amount tax,
      String reservationId,
      String paymentMethod,
      String correlationId) {}

  record Line(String variantId, int quantity, Amount unitPrice) {}

  record DiscountBody(
      String source, String couponCode, String campaignId, String campaignName, Amount amount) {}

  record Amount(long amountMinor, String currency) {

    static Amount of(Money money) {
      return new Amount(money.amountMinor(), money.currency().getCurrencyCode());
    }
  }

  /** How a run ended, as Orchestration answers it. */
  record Outcome(String status, String orderId, String declineReason) {}

  @Override
  public PaymentAttempt pay(CheckoutSession session, String paymentMethod) {
    var options =
        WorkflowOptions.newBuilder()
            .setTaskQueue(TASK_QUEUE)
            .setWorkflowId(session.id())
            .setWorkflowIdReusePolicy(
                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE)
            .setWorkflowIdConflictPolicy(
                WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
            .setMemo(Map.of(CUSTOMER_MEMO, session.customerId()))
            .build();
    var run = temporal.newUntypedWorkflowStub(WORKFLOW_TYPE, options);
    try {
      run.start(request(session, paymentMethod));
    } catch (RuntimeException e) {
      throw new CheckoutUnavailableException(e);
    }
    var customerId = session.customerId();
    try {
      return attempt(
          customerId, run.getResult(wait.toMillis(), TimeUnit.MILLISECONDS, Outcome.class));
    } catch (TimeoutException e) {
      return paidSoFar(session.id()).orElse(processing(customerId));
    } catch (WorkflowFailedException e) {
      log.error("The checkout Saga for Checkout Session {} failed", session.id(), e);
      return failed(customerId);
    } catch (RuntimeException e) {
      // It started, so it goes on without us; its outcome can be read later.
      log.warn("Lost sight of the checkout Saga for Checkout Session {}", session.id(), e);
      return processing(customerId);
    }
  }

  @Override
  public Optional<PaymentAttempt> latestAttempt(String sessionId) {
    var latest = temporal.newUntypedWorkflowStub(sessionId);
    io.temporal.client.WorkflowExecutionDescription description;
    try {
      description = latest.describe();
    } catch (WorkflowNotFoundException e) {
      return Optional.empty();
    } catch (WorkflowException e) {
      if (e.getCause() instanceof WorkflowNotFoundException) {
        return Optional.empty();
      }
      throw new CheckoutUnavailableException(e);
    } catch (RuntimeException e) {
      throw new CheckoutUnavailableException(e);
    }
    var customerId = (String) description.getMemo(CUSTOMER_MEMO, String.class);
    var paid = (Outcome) description.getMemo(OUTCOME_MEMO, Outcome.class);
    return Optional.of(
        switch (description.getStatus()) {
          case WORKFLOW_EXECUTION_STATUS_RUNNING ->
              paid != null ? attempt(customerId, paid) : processing(customerId);
          case WORKFLOW_EXECUTION_STATUS_COMPLETED -> attempt(customerId, result(latest));
          default -> failed(customerId);
        });
  }

  /** The {@code PAID} outcome a run still clearing up keeps in its memo. */
  private Optional<PaymentAttempt> paidSoFar(String sessionId) {
    try {
      return latestAttempt(sessionId).filter(a -> a.status() == Status.PAID);
    } catch (CheckoutUnavailableException e) {
      return Optional.empty();
    }
  }

  private static Outcome result(WorkflowStub completed) {
    try {
      return completed.getResult(5, TimeUnit.SECONDS, Outcome.class);
    } catch (TimeoutException | RuntimeException e) {
      throw new CheckoutUnavailableException(e);
    }
  }

  private static PaymentAttempt attempt(String customerId, Outcome outcome) {
    return new PaymentAttempt(
        customerId, Status.valueOf(outcome.status()), outcome.orderId(), outcome.declineReason());
  }

  private static PaymentAttempt processing(String customerId) {
    return new PaymentAttempt(customerId, Status.PROCESSING, null, null);
  }

  /** A run that ended without an outcome of its own: it failed, or was terminated. */
  private static PaymentAttempt failed(String customerId) {
    return new PaymentAttempt(customerId, Status.FAILED, null, null);
  }

  private static Request request(CheckoutSession session, String paymentMethod) {
    return new Request(
        session.id(),
        session.customerId(),
        session.cart().lines().stream()
            .map(l -> new Line(l.variantId(), l.quantity(), Amount.of(l.unitPrice())))
            .toList(),
        session.discounts().stream().map(TemporalCheckoutSaga::discount).toList(),
        Amount.of(session.tax()),
        session.reservationId(),
        paymentMethod,
        CorrelationId.current().orElse(null));
  }

  private static DiscountBody discount(Discount d) {
    return new DiscountBody(
        d.source().name(), d.couponCode(), d.campaignId(), d.campaignName(), Amount.of(d.amount()));
  }
}
