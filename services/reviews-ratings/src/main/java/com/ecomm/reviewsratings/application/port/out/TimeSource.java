package com.ecomm.reviewsratings.application.port.out;

import java.time.Instant;

public interface TimeSource {

  Instant now();
}
