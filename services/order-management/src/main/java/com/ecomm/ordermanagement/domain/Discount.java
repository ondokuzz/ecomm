package com.ecomm.ordermanagement.domain;

import com.ecomm.commons.money.Money;

/**
 * An amount taken off an Order, and what gave it: a Campaign, named by its ID and name, or a
 * Coupon, named by the code the Customer entered. Exactly the fields its {@code source} needs are
 * set, the others null. Throws {@link InvalidOrderException} unless every field is valid.
 */
public record Discount(
    Source source, String couponCode, String campaignId, String campaignName, Money amount) {

  /** What gave a Discount. */
  public enum Source {
    CAMPAIGN,
    COUPON
  }

  /** The longest Coupon code a Discount can hold. */
  public static final int MAX_COUPON_CODE_LENGTH = 64;

  /** The longest Campaign ID a Discount can hold. */
  public static final int MAX_CAMPAIGN_ID_LENGTH = 64;

  /** The longest Campaign name a Discount can hold, as long as Promotions allows. */
  public static final int MAX_CAMPAIGN_NAME_LENGTH = 100;

  public Discount {
    if (source == null) {
      throw new InvalidOrderException("a discount needs a source: CAMPAIGN or COUPON");
    }
    switch (source) {
      case COUPON -> {
        requireText(couponCode, "couponCode", MAX_COUPON_CODE_LENGTH);
        if (campaignId != null || campaignName != null) {
          throw new InvalidOrderException("a COUPON discount names no campaign");
        }
      }
      case CAMPAIGN -> {
        requireText(campaignId, "campaignId", MAX_CAMPAIGN_ID_LENGTH);
        requireText(campaignName, "campaignName", MAX_CAMPAIGN_NAME_LENGTH);
        if (couponCode != null) {
          throw new InvalidOrderException("a CAMPAIGN discount has no couponCode");
        }
      }
    }
    if (amount == null) {
      throw new InvalidOrderException("a discount needs an amount");
    }
    if (amount.amountMinor() < 0) {
      throw new InvalidOrderException("a discount's amount can't be negative");
    }
  }

  public static Discount coupon(String couponCode, Money amount) {
    return new Discount(Source.COUPON, couponCode, null, null, amount);
  }

  public static Discount campaign(String campaignId, String campaignName, Money amount) {
    return new Discount(Source.CAMPAIGN, null, campaignId, campaignName, amount);
  }

  private static void requireText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new InvalidOrderException("a discount needs a " + field);
    }
    if (value.length() > maxLength) {
      throw new InvalidOrderException("a " + field + " is at most " + maxLength + " characters");
    }
  }
}
