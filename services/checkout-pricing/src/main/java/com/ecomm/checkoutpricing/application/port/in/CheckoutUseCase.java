package com.ecomm.checkoutpricing.application.port.in;

import com.ecomm.checkoutpricing.domain.CheckoutResult;
import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.checkoutpricing.domain.CheckoutSessionExpiredException;
import com.ecomm.checkoutpricing.domain.CheckoutSessionNotFoundException;
import java.util.Optional;

/** Checkout in two steps: a Checkout Session holds the Customer's Cart, and paying it buys it. */
public interface CheckoutUseCase {

  /**
   * Prices the Customer's Cart, reserves its Stock and holds both in a new Checkout Session. A
   * session the Customer already has is replaced, and its Reservation released.
   */
  CheckoutSession start(String customerId);

  /** The Customer's Checkout Session, unless they have none or it has expired. */
  Optional<CheckoutSession> current(String customerId);

  /**
   * Turns the Customer's Checkout Session into a paid Order at its captured Prices, clears the Cart
   * and ends the session. If a step fails once the Order exists, the Order is cancelled and the
   * Cart and session are left as they were.
   *
   * @throws CheckoutSessionNotFoundException when the Customer has no session with this ID
   * @throws CheckoutSessionExpiredException when it has expired; nothing happens either way
   */
  CheckoutResult pay(String customerId, String sessionId);
}
