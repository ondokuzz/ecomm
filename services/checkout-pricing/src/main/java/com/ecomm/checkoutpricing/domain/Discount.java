package com.ecomm.checkoutpricing.domain;

import com.ecomm.commons.money.Money;

/** What a Coupon takes off a Checkout Session's subtotal, as Promotions worked it out. */
public record Discount(String couponCode, Money amount) {}
