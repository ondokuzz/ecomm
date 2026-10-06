package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.domain.Discount;
import com.ecomm.promotions.domain.Discount.CampaignDiscount;
import com.ecomm.promotions.domain.Discount.CouponDiscount;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Every Discount, in the order they apply: {@code {"discounts": [{"source", "amount", ...}]}}. A
 * {@code CAMPAIGN} one names the Campaign's {@code campaignId} and {@code campaignName}; a {@code
 * COUPON} one its upper-case {@code couponCode}.
 */
record DiscountResponse(List<Entry> discounts) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Entry(
      String source, UUID campaignId, String campaignName, String couponCode, Amount amount) {

    static Entry of(Discount discount) {
      return switch (discount) {
        case CampaignDiscount c ->
            new Entry("CAMPAIGN", c.campaignId(), c.campaignName(), null, Amount.of(c.amount()));
        case CouponDiscount c ->
            new Entry("COUPON", null, null, c.couponCode(), Amount.of(c.amount()));
      };
    }
  }

  static DiscountResponse of(List<Discount> discounts) {
    return new DiscountResponse(discounts.stream().map(Entry::of).toList());
  }
}
