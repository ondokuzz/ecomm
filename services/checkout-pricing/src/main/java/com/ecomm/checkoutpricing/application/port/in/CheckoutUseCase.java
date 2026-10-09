package com.ecomm.checkoutpricing.application.port.in;

import com.ecomm.checkoutpricing.domain.CheckoutResult;
import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.checkoutpricing.domain.CheckoutSessionExpiredException;
import com.ecomm.checkoutpricing.domain.CheckoutSessionNotFoundException;
import com.ecomm.checkoutpricing.domain.CouponNotApplicableException;
import com.ecomm.checkoutpricing.domain.PaymentDeclinedException;
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
   * Applies the Coupon {@code couponCode} to the Customer's Checkout Session, in place of any it
   * had, and works out its tax and total again.
   *
   * @throws CheckoutSessionNotFoundException when the Customer has no session with this ID
   * @throws CheckoutSessionExpiredException when it has expired
   * @throws CouponNotApplicableException when the Coupon doesn't apply; the session is unchanged
   */
  CheckoutSession applyCoupon(String customerId, String sessionId, String couponCode);

  /**
   * Takes the Coupon, if any, off the Customer's Checkout Session, and works out its tax and total
   * again.
   *
   * @throws CheckoutSessionNotFoundException when the Customer has no session with this ID
   * @throws CheckoutSessionExpiredException when it has expired
   */
  CheckoutSession removeCoupon(String customerId, String sessionId);

  /**
   * Turns the Customer's Checkout Session into a paid Order at its captured Prices, less its
   * Discount, paid with {@code paymentMethod}, clears the Cart and ends the session. If a step
   * fails once the Order exists, a declined payment included, the Order is cancelled and the Cart
   * and session are left as they were, so the session can be paid again.
   *
   * @throws CheckoutSessionNotFoundException when the Customer has no session with this ID
   * @throws CheckoutSessionExpiredException when it has expired; nothing happens either way
   * @throws PaymentDeclinedException when the gateway declines the payment method
   */
  CheckoutResult pay(String customerId, String sessionId, String paymentMethod);

  /**
   * Ends the Customer's Checkout Session {@code sessionId} once its Order is paid, but only while
   * the Customer's pointer still names it: a session they have since replaced, or one that is gone,
   * is left as it is.
   */
  void end(String customerId, String sessionId);
}
