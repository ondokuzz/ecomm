package com.ecomm.checkoutpricing.domain;

/** The Order a checkout produced, and the Order Status it ended in. */
public record CheckoutResult(String orderId, OrderStatus status) {}
