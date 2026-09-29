package com.ecomm.inventory.domain;

/**
 * A stock request (a batch, a Reservation or an on-hand count) was described with missing or
 * malformed data.
 */
public class InvalidStockRequestException extends RuntimeException {

  public InvalidStockRequestException(String message) {
    super(message);
  }
}
