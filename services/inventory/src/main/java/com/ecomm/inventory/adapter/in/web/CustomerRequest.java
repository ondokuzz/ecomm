package com.ecomm.inventory.adapter.in.web;

/**
 * The owner a caller acts for when it commits or releases a Reservation: {@code {"customerId"}}, a
 * Customer's ID or, for an RMA's Reservation, {@code rma:<rmaId>}.
 */
record CustomerRequest(String customerId) {}
