package com.ecomm.template.domain;

import java.time.Instant;
import java.util.Objects;

/** A service's answer to a ping: who answered, which Customer asked, and when. */
public record Pong(String service, String customerId, Instant at) {

  public Pong {
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(customerId, "customerId");
    Objects.requireNonNull(at, "at");
  }
}
