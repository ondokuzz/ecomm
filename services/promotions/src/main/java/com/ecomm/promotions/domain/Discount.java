package com.ecomm.promotions.domain;

import com.ecomm.commons.money.Money;
import java.util.UUID;

/** What a Campaign or a Coupon takes off a Checkout Session, and which one it was. */
public sealed interface Discount {

  Money amount();

  /** A running Campaign's Discount, under its ID and name. */
  record CampaignDiscount(UUID campaignId, String campaignName, Money amount) implements Discount {}

  /** The Coupon's Discount, under its upper-case code. */
  record CouponDiscount(String couponCode, Money amount) implements Discount {}
}
