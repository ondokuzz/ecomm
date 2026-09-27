package com.ecomm.checkoutpricing.domain;

import java.util.Objects;

/**
 * The Customer checking out, and the access token that speaks for them to Cart. Only Cart sees the
 * token; every other service hears about the Customer by {@code id}.
 */
public record Customer(String id, String accessToken) {

  public Customer {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(accessToken, "accessToken");
  }

  @Override
  public String toString() {
    return "Customer[id=" + id + "]";
  }
}
