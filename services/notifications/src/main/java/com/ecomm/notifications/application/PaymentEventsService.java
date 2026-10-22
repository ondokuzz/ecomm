package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.in.PaymentEventsUseCase;
import com.ecomm.notifications.application.port.out.NotificationPreferencesStore;
import com.ecomm.notifications.application.port.out.NotificationStore;
import com.ecomm.notifications.application.port.out.TimeSource;
import com.ecomm.notifications.application.port.out.Transactions;
import com.ecomm.notifications.domain.PaymentRefunded;
import java.util.List;

/**
 * A Payment's {@code REFUNDED} event records {@code PAYMENT_REFUNDED} for the Refund it was
 * published for: its last approved {@code REFUND}. Nothing else is announced: a Capture is covered
 * by {@code ORDER_SHIPPED}, and a declined Payment the Customer saw at checkout.
 */
public class PaymentEventsService implements PaymentEventsUseCase {

  private final Ledger ledger;
  private final Transactions transactions;

  public PaymentEventsService(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      Transactions transactions,
      TimeSource time) {
    this.ledger = new Ledger(notifications, preferences, time);
    this.transactions = transactions;
  }

  @Override
  public List<String> paymentChanged(PaymentChanged event) {
    if (!"REFUNDED".equals(event.change())) {
      return List.of();
    }
    var refund =
        event.transactions().reversed().stream()
            .filter(t -> "REFUND".equals(t.kind()) && "APPROVED".equals(t.outcome()))
            .findFirst();
    if (refund.isEmpty()) {
      return List.of();
    }
    var content =
        new PaymentRefunded(
            event.orderId(),
            event.customerId(),
            refund.get().amount(),
            refund.get().reference(),
            refund.get().at());
    return transactions.inTransaction(() -> ledger.record(event.eventId(), content));
  }
}
