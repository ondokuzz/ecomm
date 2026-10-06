package com.ecomm.promotions.application.port.in;

import com.ecomm.promotions.domain.CouponNotApplicableException;
import com.ecomm.promotions.domain.Discount;
import com.ecomm.promotions.domain.Line;
import java.util.List;
import java.util.Optional;

/** Checkout asks for every Discount a Checkout Session's lines are due. */
public interface EvaluateDiscountUseCase {

  /**
   * The Discounts of every Campaign running now, then of the Coupon with {@code couponCode}, in any
   * case, if there is one, in the order they apply (see {@link
   * com.ecomm.promotions.domain.DiscountStacking}).
   *
   * @throws CouponNotApplicableException when there is no such Coupon or it doesn't apply
   */
  List<Discount> evaluate(List<Line> lines, Optional<String> couponCode);
}
