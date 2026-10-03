package com.ecomm.promotions.application.port.out;

import java.time.Instant;

/** Promotions' clock, which says whether a Coupon or a Campaign is valid yet, or still. */
public interface TimeSource {

  Instant now();
}
