package com.ecomm.checkoutpricing.adapter.in.web;

/** A paid Checkout Session: its Order, and that Order's Order Status, {@code PAID}. */
record CheckoutResponse(String orderId, String status) {}
