package com.ecomm.inventory.adapter.in.web;

/**
 * The Customer Checkout acts for when it commits or releases a Reservation: {@code {"customerId"}}.
 */
record CustomerRequest(String customerId) {}
