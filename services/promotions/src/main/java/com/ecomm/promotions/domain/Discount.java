package com.ecomm.promotions.domain;

import com.ecomm.commons.money.Money;

/** What a Coupon takes off a subtotal, with the Coupon's code. */
public record Discount(String couponCode, Money amount) {}
