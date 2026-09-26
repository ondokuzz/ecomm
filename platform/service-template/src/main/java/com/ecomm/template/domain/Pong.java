package com.ecomm.template.domain;

import java.time.Instant;
import java.util.Objects;

/** A service's answer to a ping: who answered, and when. */
public record Pong(String service, Instant at) {

  public Pong {
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(at, "at");
  }
}
