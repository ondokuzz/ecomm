package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.CouponNotApplicableException;
import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.checkoutpricing.domain.PricedLine;
import java.util.List;
import java.util.Optional;

/** Promotions, which owns Campaigns and Coupons and says what they take off. */
public interface PromotionsPort {

  /**
   * Every Discount {@code lines} are due, in the order they apply: the running Campaigns', then the
   * Coupon {@code couponCode}'s, last, when there is one.
   *
   * @throws CouponNotApplicableException when the Coupon doesn't apply, with Promotions' reason
   */
  List<Discount> evaluate(List<PricedLine> lines, Optional<String> couponCode);
}
