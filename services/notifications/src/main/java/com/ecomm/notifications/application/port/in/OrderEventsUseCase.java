package com.ecomm.notifications.application.port.in;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.List;

/** Records the Notifications an Order's event triggers. */
public interface OrderEventsUseCase {

  /** An Order's event: why it was published, and the Order then. */
  record OrderChanged(
      String eventId,
      String orderId,
      String change,
      String customerId,
      String status,
      Instant placedAt,
      List<Line> lines,
      Money subtotal,
      List<Money> discounts,
      Money shipping,
      Money tax,
      Money total) {

    public record Line(String variantId, int quantity, Money unitPrice) {}
  }

  /** The Notifications recorded for it: none for a change that triggers nothing, or a repeat. */
  List<String> orderChanged(OrderChanged event);
}
