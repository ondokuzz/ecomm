package com.ecomm.commons.security;

import java.util.Objects;

/**
 * The Customer making the request, taken from the access token. Declare it as a controller method
 * parameter to receive it; {@code id} is the token's {@code sub}, which is the Customer ID.
 */
public record CurrentCustomer(String id) {

  public CurrentCustomer {
    Objects.requireNonNull(id, "id");
  }
}
