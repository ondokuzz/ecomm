package com.ecomm.checkoutpricing.application.port.in;

import com.ecomm.checkoutpricing.domain.CheckoutSession;
import com.ecomm.checkoutpricing.domain.CheckoutSessionExpiredException;
import com.ecomm.checkoutpricing.domain.CheckoutSessionNotFoundException;
import com.ecomm.checkoutpricing.domain.CouponNotApplicableException;
import com.ecomm.checkoutpricing.domain.NoPaymentAttemptException;
import com.ecomm.checkoutpricing.domain.PaymentAttempt;
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
   * Pays the Customer's Checkout Session with {@code paymentMethod}: starts the checkout Saga with
   * the session as it stands, which turns it into a paid Order at its captured Prices, less its
   * Discounts, or joins the attempt still running for it, and waits a while for how it ends. A
   * session already paid isn't paid again: its paid attempt is the answer. After a decline, the
   * session and its Reservation stay, so it can be paid again.
   *
   * @return the attempt, {@code PROCESSING} while the Saga is still going
   * @throws CheckoutSessionNotFoundException when the Customer has no session with this ID
   * @throws CheckoutSessionExpiredException when it has expired; nothing is started either way
   * @throws CheckoutUnavailableException when the Saga can't be started; nothing is
   */
  PaymentAttempt pay(String customerId, String sessionId, String paymentMethod);

  /**
   * The latest attempt to pay the Customer's Checkout Session {@code sessionId}, even once the
   * session has ended.
   *
   * @throws NoPaymentAttemptException when it was never paid, or isn't theirs
   * @throws CheckoutUnavailableException when the Saga can't be read
   */
  PaymentAttempt latestPayment(String customerId, String sessionId);

  /**
   * Ends the Customer's Checkout Session {@code sessionId} once its Order is paid, but only while
   * the Customer's pointer still names it: a session they have since replaced, or one that is gone,
   * is left as it is.
   */
  void end(String customerId, String sessionId);
}
