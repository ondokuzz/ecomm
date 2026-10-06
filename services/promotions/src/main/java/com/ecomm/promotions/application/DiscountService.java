package com.ecomm.promotions.application;

import com.ecomm.promotions.application.port.in.EvaluateDiscountUseCase;
import com.ecomm.promotions.application.port.out.CampaignRepository;
import com.ecomm.promotions.application.port.out.CouponRepository;
import com.ecomm.promotions.application.port.out.TimeSource;
import com.ecomm.promotions.domain.Coupon;
import com.ecomm.promotions.domain.CouponNotApplicableException;
import com.ecomm.promotions.domain.CouponRejection;
import com.ecomm.promotions.domain.Discount;
import com.ecomm.promotions.domain.DiscountStacking;
import com.ecomm.promotions.domain.Line;
import java.util.List;
import java.util.Optional;

public class DiscountService implements EvaluateDiscountUseCase {

  private final CampaignRepository campaigns;
  private final CouponRepository coupons;
  private final TimeSource time;

  public DiscountService(CampaignRepository campaigns, CouponRepository coupons, TimeSource time) {
    this.campaigns = campaigns;
    this.coupons = coupons;
    this.time = time;
  }

  @Override
  public List<Discount> evaluate(List<Line> lines, Optional<String> couponCode) {
    var coupon =
        couponCode.map(
            code ->
                coupons
                    .find(Coupon.normalize(code))
                    .orElseThrow(
                        () -> new CouponNotApplicableException(code, CouponRejection.UNKNOWN)));
    return DiscountStacking.discounts(lines, campaigns.all(), coupon, time.now());
  }
}
