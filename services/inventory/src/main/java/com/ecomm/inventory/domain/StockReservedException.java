package com.ecomm.inventory.domain;

/** Inventory can't stop stocking a Variant while Reservations hold some of it. */
public class StockReservedException extends RuntimeException {

  private final int reserved;

  public StockReservedException(String variantId, int reserved) {
    super(
        "Reservations hold "
            + reserved
            + " of "
            + variantId
            + ", so its Stock stays until they are committed, released or expire");
    this.reserved = reserved;
  }

  public int reserved() {
    return reserved;
  }
}
