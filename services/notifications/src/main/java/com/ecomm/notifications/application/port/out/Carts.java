package com.ecomm.notifications.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Customers' Carts, as Cart answers them now. */
public interface Carts {

  /** A Cart's lines, in Variant ID order, and when it last changed. */
  record CustomerCart(List<Line> lines, Instant lastChangedAt) {

    public CustomerCart {
      lines = List.copyOf(lines);
    }

    public record Line(String variantId, int quantity) {}
  }

  /**
   * The Customer's Cart; empty when they have none, or it is empty.
   *
   * @throws CartsUnavailableException when Cart can't answer now
   */
  Optional<CustomerCart> cartOf(String customerId);

  /** Cart didn't answer; asking again later may work. */
  class CartsUnavailableException extends RuntimeException {

    public CartsUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
