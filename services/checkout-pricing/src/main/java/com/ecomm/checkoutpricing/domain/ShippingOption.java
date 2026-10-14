package com.ecomm.checkoutpricing.domain;

import com.ecomm.commons.money.Money;

/**
 * One way to send a Checkout Session's Order, as Fulfillment & Shipping quoted it for its Shipping
 * address: its ID, such as {@code STANDARD} or {@code EXPRESS}, its Carrier, its Shipping cost and
 * how many days it should take.
 */
public record ShippingOption(String id, String carrier, Money cost, int estimatedDays) {}
