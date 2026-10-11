package com.ecomm.recommendations.domain;

/** An Order's Status, as Order Management publishes it. */
public enum OrderStatus {
  PLACED,
  PAID,
  FULFILLED,
  SHIPPED,
  DELIVERED,
  CANCELLED,
  RETURNED,
  /** A Status Order Management added after this service was built; it doesn't count. */
  UNKNOWN;

  /** The Status by its name in an event; one this service doesn't know is {@link #UNKNOWN}. */
  public static OrderStatus of(String name) {
    for (var status : values()) {
      if (status != UNKNOWN && status.name().equals(name)) {
        return status;
      }
    }
    return UNKNOWN;
  }

  /**
   * Whether an Order in this Status counts towards what was bought together: paid, and kept. The
   * same rule as Reviews & Ratings'.
   */
  public boolean counts() {
    return switch (this) {
      case PAID, FULFILLED, SHIPPED, DELIVERED -> true;
      case PLACED, CANCELLED, RETURNED, UNKNOWN -> false;
    };
  }
}
