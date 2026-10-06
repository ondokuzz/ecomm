package com.ecomm.checkoutpricing.domain;

import com.ecomm.commons.money.Money;
import java.util.Currency;
import java.util.List;

/**
 * What a running Campaign or the applied Coupon takes off a Checkout Session, as Promotions worked
 * it out: a Campaign's names it by ID and name, a Coupon's by its code, and the other fields are
 * null.
 */
public record Discount(
    Source source, String couponCode, String campaignId, String campaignName, Money amount) {

  /** What gave a Discount. */
  public enum Source {
    CAMPAIGN,
    COUPON
  }

  public static Discount campaign(String campaignId, String campaignName, Money amount) {
    return new Discount(Source.CAMPAIGN, null, campaignId, campaignName, amount);
  }

  public static Discount coupon(String couponCode, Money amount) {
    return new Discount(Source.COUPON, couponCode, null, null, amount);
  }

  /** What {@code discounts} take off together, in {@code currency}. */
  public static Money total(List<Discount> discounts, Currency currency) {
    long sum = 0;
    for (var discount : discounts) {
      sum = Math.addExact(sum, discount.amount().amountMinor());
    }
    return new Money(sum, currency);
  }
}
