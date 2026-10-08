package com.ecomm.payment.application;

import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.payment.application.port.in.AuthorizePaymentUseCase;
import com.ecomm.payment.application.port.in.BrowsePaymentsUseCase;
import com.ecomm.payment.application.port.in.FindPaymentUseCase;
import com.ecomm.payment.application.port.in.PublishBackfillUseCase;
import com.ecomm.payment.application.port.in.VoidPaymentUseCase;
import com.ecomm.payment.application.port.out.PaymentEvent;
import com.ecomm.payment.application.port.out.PaymentEvent.Change;
import com.ecomm.payment.application.port.out.PaymentGatewayPort;
import com.ecomm.payment.application.port.out.PaymentRepository;
import com.ecomm.payment.application.port.out.TimeSource;
import com.ecomm.payment.application.port.out.Transactions;
import com.ecomm.payment.domain.AuthorizationRequest;
import com.ecomm.payment.domain.Payment;
import com.ecomm.payment.domain.PaymentNotVoidableException;
import com.ecomm.payment.domain.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PaymentService
    implements AuthorizePaymentUseCase,
        VoidPaymentUseCase,
        FindPaymentUseCase,
        BrowsePaymentsUseCase,
        PublishBackfillUseCase {

  /** How many Payments the backfill publishes per transaction. */
  private static final int BACKFILL_BATCH = 100;

  private final PaymentGatewayPort gateway;
  private final PaymentRepository payments;
  private final Transactions transactions;
  private final IntegrationEventPublisher events;
  private final TimeSource time;

  public PaymentService(
      PaymentGatewayPort gateway,
      PaymentRepository payments,
      Transactions transactions,
      IntegrationEventPublisher events,
      TimeSource time) {
    this.gateway = gateway;
    this.payments = payments;
    this.transactions = transactions;
    this.events = events;
    this.time = time;
  }

  @Override
  public Payment authorize(AuthorizationRequest request, String idempotencyKey) {
    var answer = gateway.authorize(request, idempotencyKey);
    var payment = Payment.fromAuthorization(UUID.randomUUID(), request, answer, time.now());
    return transactions.inTransaction(
        () -> {
          payments.add(payment);
          events.publish(PaymentEvent.of(payment, changeOf(payment.status())));
          return payment;
        });
  }

  /**
   * The Payment stays locked while the gateway is asked, so two voids of one Payment ask it once.
   * The gateway gets the Payment's own key for its void, since a Payment is voided at most once.
   */
  @Override
  public Optional<Payment> voidPayment(String customerId, UUID id) {
    return transactions.inTransaction(
        () ->
            payments
                .lockToChange(id)
                .filter(payment -> payment.belongsTo(customerId))
                .map(
                    payment -> {
                      if (payment.status() == PaymentStatus.VOIDED) {
                        return payment;
                      }
                      if (!payment.isVoidable()) {
                        throw new PaymentNotVoidableException(id, payment.status());
                      }
                      var reference =
                          gateway.voidAuthorization(payment.gatewayReference(), "void:" + id);
                      var voided = payment.voided(reference, time.now());
                      payments.recordLatestTransaction(voided);
                      events.publish(PaymentEvent.of(voided, Change.VOIDED));
                      return voided;
                    }));
  }

  @Override
  public Optional<Payment> payment(String customerId, UUID id) {
    return payments.find(id).filter(payment -> payment.belongsTo(customerId));
  }

  @Override
  public List<Payment> payments(String customerId, String orderId) {
    return payments.findByOrder(orderId).stream()
        .filter(payment -> payment.belongsTo(customerId))
        .toList();
  }

  @Override
  public List<Payment> paymentsForOrder(String orderId) {
    return payments.findByOrder(orderId);
  }

  @Override
  public int publishBackfill() {
    var published = 0;
    while (true) {
      var batch =
          transactions.inTransaction(
              () -> {
                var awaiting = payments.lockAwaitingBackfillEvent(BACKFILL_BATCH);
                awaiting.forEach(
                    payment -> events.publish(PaymentEvent.of(payment, Change.BACKFILLED)));
                payments.markBackfillPublished(awaiting.stream().map(Payment::id).toList());
                return awaiting.size();
              });
      if (batch == 0) {
        return published;
      }
      published += batch;
    }
  }

  private static Change changeOf(PaymentStatus status) {
    return switch (status) {
      case AUTHORIZED -> Change.AUTHORIZED;
      case DECLINED -> Change.DECLINED;
      case PENDING -> Change.PENDING;
      case VOIDED -> throw new IllegalStateException("An authorization can't void a Payment");
    };
  }
}
