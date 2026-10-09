package com.ecomm.checkoutpricing.adapter.out.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUnavailableException;
import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.checkoutpricing.domain.PaymentAttempt;
import com.ecomm.checkoutpricing.domain.PaymentAttempt.Status;
import com.ecomm.checkoutpricing.domain.PricedCart;
import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.commons.money.Money;
import com.ecomm.commons.web.CorrelationId;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.RpcRetryOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * The Temporal adapter against Temporal's own test server, with a stand-in for Orchestration's
 * {@code checkout} workflow on the {@code checkout} task queue: it starts the workflow named by the
 * session with the session's input, joins it while it runs, starts it again once it has closed, and
 * reads how it ended.
 */
class TemporalCheckoutSagaTest {

  private static TestWorkflowEnvironment temporal;

  private TemporalCheckoutSaga saga;
  private CheckoutSession session;

  @BeforeAll
  static void startTemporal() {
    temporal =
        TestWorkflowEnvironment.newInstance(
            TestEnvironmentOptions.newBuilder().setUseTimeskipping(false).build());
    temporal.newWorker("checkout").registerWorkflowImplementationTypes(StandIn.class);
    temporal.start();
  }

  @AfterAll
  static void stopTemporal() {
    temporal.close();
  }

  @BeforeEach
  void newSession() {
    saga = new TemporalCheckoutSaga(temporal.getWorkflowClient(), Duration.ofMillis(500));
    session =
        new CheckoutSession(
            UUID.randomUUID().toString(),
            "customer-42",
            new PricedCart(
                List.of(new PricedLine("PHN-PIXEL-9", "PHN-PIXEL-9", "phones", 2, eur(79900)))),
            List.of(
                Discount.campaign("c-1", "Audio week", eur(3735)),
                Discount.coupon("WELCOME10", eur(15980))),
            eur(0),
            "5e2d1c0b-0000-4000-8000-000000000001",
            Instant.parse("2030-01-01T00:00:00Z"));
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void payingStartsTheCheckoutWorkflowNamedBySessionWithItsInput() {
    MDC.put(CorrelationId.MDC_KEY, "pay-7f3a-42");

    saga.pay(session, StandIn.KEEP_RUNNING);

    assertThat(StandIn.inputs(session.id()))
        .containsExactly(
            Map.of(
                "checkoutSessionId",
                session.id(),
                "customerId",
                "customer-42",
                "lines",
                List.of(
                    Map.of(
                        "variantId",
                        "PHN-PIXEL-9",
                        "quantity",
                        2,
                        "unitPrice",
                        Map.of("amountMinor", 79900, "currency", "EUR"))),
                "discounts",
                List.of(
                    discount("CAMPAIGN", null, "c-1", "Audio week", 3735),
                    discount("COUPON", "WELCOME10", null, null, 15980)),
                "tax",
                Map.of("amountMinor", 0, "currency", "EUR"),
                "reservationId",
                "5e2d1c0b-0000-4000-8000-000000000001",
                "paymentMethod",
                StandIn.KEEP_RUNNING,
                "correlationId",
                "pay-7f3a-42"));
  }

  @Test
  void anOutcomeThatComesInTimeIsTheAnswer() {
    assertThat(saga.pay(session, "PAID"))
        .isEqualTo(new PaymentAttempt("customer-42", Status.PAID, "order-1", null));
    assertThat(saga.pay(sessionWith("DECLINED"), "DECLINED").status()).isEqualTo(Status.DECLINED);
  }

  @Test
  void anOutcomeStillToComeIsProcessingAndAPayWhileItRunsJoinsIt() {
    assertThat(saga.pay(session, StandIn.KEEP_RUNNING).status()).isEqualTo(Status.PROCESSING);

    assertThat(saga.pay(session, StandIn.KEEP_RUNNING).status()).isEqualTo(Status.PROCESSING);

    assertThat(StandIn.inputs(session.id())).hasSize(1);
    assertThat(saga.latestAttempt(session.id()))
        .contains(new PaymentAttempt("customer-42", Status.PROCESSING, null, null));
  }

  @Test
  void theLatestAttemptIsReadOnceItEnds() {
    saga.pay(session, StandIn.KEEP_RUNNING);

    finish(Map.of("status", "DECLINED", "orderId", "order-1", "declineReason", "card_declined"));

    assertThat(saga.latestAttempt(session.id()))
        .contains(new PaymentAttempt("customer-42", Status.DECLINED, "order-1", "card_declined"));
  }

  @Test
  void aSessionWhoseAttemptHasEndedIsPaidAgainInANewRun() {
    saga.pay(session, StandIn.KEEP_RUNNING);
    finish(Map.of("status", "DECLINED", "orderId", "order-1", "declineReason", "card_declined"));

    assertThat(saga.pay(session, "PAID").status()).isEqualTo(Status.PAID);

    assertThat(StandIn.inputs(session.id())).hasSize(2);
    assertThat(saga.latestAttempt(session.id()).map(PaymentAttempt::status)).contains(Status.PAID);
  }

  @Test
  void aRunThatHasPaidTheOrderIsPaidWhileItStillClearsUp() {
    saga.pay(session, StandIn.KEEP_RUNNING);

    stub().markPaid(Map.of("status", "PAID", "orderId", "order-1"));

    await()
        .untilAsserted(
            () ->
                assertThat(saga.latestAttempt(session.id()))
                    .contains(new PaymentAttempt("customer-42", Status.PAID, "order-1", null)));
    assertThat(saga.pay(session, StandIn.KEEP_RUNNING).status()).isEqualTo(Status.PAID);
  }

  @Test
  void aSessionNeverPaidHasNoAttempt() {
    assertThat(saga.latestAttempt(session.id())).isEmpty();
  }

  @Test
  void anUnreachableTemporalIsUnavailableAndStartsNothing() {
    var nowhere =
        WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget("localhost:1")
                .setRpcRetryOptions(
                    RpcRetryOptions.newBuilder().setExpiration(Duration.ofSeconds(1)).build())
                .build());
    var unreachable =
        new TemporalCheckoutSaga(WorkflowClient.newInstance(nowhere), Duration.ofMillis(500));

    assertThatThrownBy(() -> unreachable.pay(session, "PAID"))
        .isInstanceOf(CheckoutUnavailableException.class);
    assertThatThrownBy(() -> unreachable.latestAttempt(session.id()))
        .isInstanceOf(CheckoutUnavailableException.class);
    assertThat(StandIn.inputs(session.id())).isEmpty();
    nowhere.shutdownNow();
  }

  private void finish(Map<String, Object> outcome) {
    stub().finish(outcome);
    temporal.getWorkflowClient().newUntypedWorkflowStub(session.id()).getResult(Map.class);
  }

  private StandInWorkflow stub() {
    return temporal.getWorkflowClient().newWorkflowStub(StandInWorkflow.class, session.id());
  }

  private CheckoutSession sessionWith(String id) {
    return new CheckoutSession(
        UUID.randomUUID() + "-" + id,
        session.customerId(),
        session.cart(),
        session.discounts(),
        session.tax(),
        session.reservationId(),
        session.expiresAt());
  }

  private static Money eur(long amountMinor) {
    return Money.of(amountMinor, "EUR");
  }

  private static Map<String, Object> discount(
      String source, String couponCode, String campaignId, String campaignName, long amountMinor) {
    var discount = new HashMap<String, Object>();
    discount.put("source", source);
    discount.put("couponCode", couponCode);
    discount.put("campaignId", campaignId);
    discount.put("campaignName", campaignName);
    discount.put("amount", Map.of("amountMinor", (int) amountMinor, "currency", "EUR"));
    return discount;
  }

  /**
   * Orchestration's {@code checkout} workflow as Checkout sees it: a type, an input, an outcome.
   */
  @WorkflowInterface
  public interface StandInWorkflow {

    @WorkflowMethod(name = "checkout")
    Map<String, Object> checkout(Map<String, Object> request);

    @SignalMethod
    void markPaid(Map<String, Object> outcome);

    @SignalMethod
    void finish(Map<String, Object> outcome);
  }

  /**
   * Keeps every input it was started with. Paid with {@value #KEEP_RUNNING} it runs until told how
   * it ends; paid with an outcome's status, it ends so at once, for the Order {@code order-1}.
   */
  public static class StandIn implements StandInWorkflow {

    static final String KEEP_RUNNING = "tok_keep_running";

    private static final Map<String, List<Map<String, Object>>> INPUTS = new ConcurrentHashMap<>();

    private Map<String, Object> outcome;

    static List<Map<String, Object>> inputs(String sessionId) {
      return INPUTS.getOrDefault(sessionId, List.of());
    }

    @Override
    public Map<String, Object> checkout(Map<String, Object> request) {
      INPUTS
          .computeIfAbsent(Workflow.getInfo().getWorkflowId(), id -> new CopyOnWriteArrayList<>())
          .add(request);
      var method = (String) request.get("paymentMethod");
      if (!KEEP_RUNNING.equals(method)) {
        return Map.of("status", method, "orderId", "order-1");
      }
      Workflow.await(() -> outcome != null);
      return outcome;
    }

    @Override
    public void markPaid(Map<String, Object> paid) {
      Workflow.upsertMemo(Map.of("outcome", paid));
    }

    @Override
    public void finish(Map<String, Object> outcome) {
      this.outcome = outcome;
    }
  }
}
