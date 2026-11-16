package com.ecomm.checkoutpricing.application.port.out;

/**
 * The checkout funnel: how many Checkout Sessions reach each step, so Staff can tell where
 * Customers stop. A session counts once per step it reaches, however often the Customer repeats it,
 * or reads how it went.
 */
public interface CheckoutFunnel {

  enum Step {
    STARTED,
    /** Given a Shipping address that was quoted. */
    ADDRESSED,
    /** Given a quoted Shipping option. */
    OPTION_CHOSEN,
    /** Handed to the checkout Saga to be paid. */
    PAY_ATTEMPTED,
    PAID,
    DECLINED,
    /** The Reservation stopped holding the Stock while it was being paid. */
    HOLD_EXPIRED,
    /** Found expired, or dropped by its store once its time ran out. */
    EXPIRED
  }

  /** Counts the session {@code sessionId} at {@code step}, unless it was counted there already. */
  void reached(String sessionId, Step step);
}
