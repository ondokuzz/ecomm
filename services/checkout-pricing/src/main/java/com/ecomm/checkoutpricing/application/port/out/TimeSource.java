package com.ecomm.checkoutpricing.application.port.out;

import java.time.Instant;

/** Checkout's clock, which says when a Checkout Session expires. */
public interface TimeSource {

  Instant now();
}
