package com.ecomm.payment.application.port.out;

import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.commons.money.Money;
import com.ecomm.payment.domain.Payment;
import java.util.List;

/**
 * The {@code payment.payment} event: a snapshot of a Payment with its Payment transactions,
 * published with each change and once for every Payment that existed before Payment events. Its
 * shape is defined by {@code platform/event-schemas/schemas/payment.payment.json}.
 */
public record PaymentEvent(String paymentId, long version, Change change, Snapshot payment)
    implements IntegrationEvent {

  public static final String TOPIC = "payment.payment";

  /** Why the event was published. */
  public enum Change {
    AUTHORIZED,
    DECLINED,
    PENDING,
    VOIDED,
    BACKFILLED
  }

  /** {@code declineReason} is null, so left out, unless the Payment was declined. */
  public record Snapshot(
      String orderId,
      String customerId,
      Amount amount,
      String status,
      String declineReason,
      List<PaymentTransaction> transactions) {}

  /** One Payment transaction; the null fields are left out. */
  public record PaymentTransaction(
      String kind,
      Amount amount,
      String outcome,
      String gatewayReference,
      String declineReason,
      String gatewayEventId,
      String at,
      boolean backfilled) {}

  public record Amount(long amountMinor, String currency) {

    static Amount of(Money money) {
      return new Amount(money.amountMinor(), money.currency().getCurrencyCode());
    }
  }

  public static PaymentEvent of(Payment payment, Change change) {
    return new PaymentEvent(
        payment.id().toString(),
        payment.version(),
        change,
        new Snapshot(
            payment.orderId(),
            payment.customerId(),
            Amount.of(payment.amount()),
            payment.status().name(),
            payment.declineReason(),
            payment.transactions().stream()
                .map(
                    t ->
                        new PaymentTransaction(
                            t.kind().name(),
                            Amount.of(t.amount()),
                            t.outcome().name(),
                            t.gatewayReference(),
                            t.declineReason(),
                            t.gatewayEventId(),
                            t.at().toString(),
                            t.backfilled()))
                .toList()));
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public String aggregateId() {
    return paymentId;
  }
}
