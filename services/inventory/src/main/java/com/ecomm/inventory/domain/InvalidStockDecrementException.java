package com.ecomm.inventory.domain;

/** A stock decrement was described with missing or malformed data. */
public class InvalidStockDecrementException extends RuntimeException {

  public InvalidStockDecrementException(String message) {
    super(message);
  }
}
