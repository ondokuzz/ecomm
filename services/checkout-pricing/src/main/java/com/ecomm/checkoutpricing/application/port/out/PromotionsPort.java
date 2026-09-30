package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.CouponNotApplicableException;
import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.commons.money.Money;

/** Promotions, which owns Coupons and says what one takes off. */
public interface PromotionsPort {

  /**
   * The Discount the Coupon {@code couponCode} gives on {@code subtotal}.
   *
   * @throws CouponNotApplicableException when it doesn't apply, with Promotions' reason
   */
  Discount evaluate(String couponCode, Money subtotal);
}
