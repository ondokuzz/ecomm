package com.ecomm.inventory.domain;

/** On-hand Stock can't go below what Reservations hold of it. */
public class OnHandBelowReservedException extends RuntimeException {

  private final String variantId;
  private final int reserved;

  public OnHandBelowReservedException(String variantId, int reserved) {
    super(
        "Reservations hold "
            + reserved
            + " of "
            + variantId
            + ", more than that new on-hand count");
    this.variantId = variantId;
    this.reserved = reserved;
  }

  public String variantId() {
    return variantId;
  }

  public int reserved() {
    return reserved;
  }
}
