package com.ecomm.notifications.application.port.in;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.List;

/** Records the Notifications a Payment's event triggers. */
public interface PaymentEventsUseCase {

  /** A Payment's event: why it was published, and its Payment transactions, oldest first. */
  record PaymentChanged(
      String eventId,
      String change,
      String orderId,
      String customerId,
      List<Transaction> transactions) {

    /** {@code reference} is what a {@code REFUND} was for, or {@code null}. */
    public record Transaction(
        String kind, String outcome, Money amount, String reference, Instant at) {}
  }

  /** The Notifications recorded for it: none for a change that triggers nothing, or a repeat. */
  List<String> paymentChanged(PaymentChanged event);
}
