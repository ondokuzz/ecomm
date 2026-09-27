package com.ecomm.checkoutpricing.domain;

/**
 * The Order Statuses Checkout moves a placed Order to; Order Management owns the rest, and places
 * every Order in {@code PLACED}.
 */
public enum OrderStatus {
  PAID,
  CANCELLED
}
