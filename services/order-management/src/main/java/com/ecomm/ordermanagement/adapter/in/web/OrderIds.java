package com.ecomm.ordermanagement.adapter.in.web;

import java.util.Optional;
import java.util.UUID;

/** Reads an Order ID from a path. */
final class OrderIds {

  private OrderIds() {}

  /** The ID as a UUID; empty when it isn't one, which callers answer as an unknown Order. */
  static Optional<UUID> parse(String id) {
    try {
      return Optional.of(UUID.fromString(id));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
