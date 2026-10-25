package com.ecomm.cart.application.port.out;

import java.time.Instant;

public interface TimeSource {

  Instant now();
}
