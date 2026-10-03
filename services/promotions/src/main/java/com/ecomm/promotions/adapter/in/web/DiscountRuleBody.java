package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.domain.DiscountRule;
import com.ecomm.promotions.domain.InvalidPromotionException;

/**
 * A Coupon's or a Campaign's {@code discount}: {@code {"type": "PERCENT_OFF", "percentOff"}} or
 * {@code {"type": "AMOUNT_OFF", "amountOff": {"amountMinor", "currency"}}}. Sent raw, so nothing is
 * coerced; read back with the other field null.
 */
record DiscountRuleBody(Object type, Object percentOff, Amount amountOff) {

  static DiscountRuleBody of(DiscountRule discount) {
    return switch (discount) {
      case DiscountRule.PercentOff p -> new DiscountRuleBody("PERCENT_OFF", p.percent(), null);
      case DiscountRule.AmountOff a ->
          new DiscountRuleBody("AMOUNT_OFF", null, Amount.of(a.amount()));
    };
  }

  /** {@code body} as a rule, or null when there is none, for the domain to name. */
  static DiscountRule toRule(DiscountRuleBody body) {
    return body == null ? null : body.toRule();
  }

  private DiscountRule toRule() {
    if ("PERCENT_OFF".equals(type)) {
      if (!(percentOff instanceof Integer percent)) {
        throw new InvalidPromotionException(
            "discount.percentOff", "must be an integer from 1 to 100");
      }
      return new DiscountRule.PercentOff(percent);
    }
    if ("AMOUNT_OFF".equals(type)) {
      return new DiscountRule.AmountOff(
          amountOff == null ? null : amountOff.toMoney("discount.amountOff"));
    }
    throw new InvalidPromotionException("discount.type", "must be PERCENT_OFF or AMOUNT_OFF");
  }
}
