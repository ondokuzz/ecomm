package com.ecomm.promotions.application.port.in;

import com.ecomm.commons.money.Money;
import com.ecomm.promotions.domain.CouponNotApplicableException;
import com.ecomm.promotions.domain.Discount;

/** Checkout asks what a Coupon takes off a Checkout Session's subtotal. */
public interface EvaluateDiscountUseCase {

  /**
   * The Discount the Coupon with {@code couponCode}, in any case, gives on {@code subtotal} now.
   *
   * @throws CouponNotApplicableException when there is no such Coupon or it doesn't apply
   */
  Discount evaluate(String couponCode, Money subtotal);
}
