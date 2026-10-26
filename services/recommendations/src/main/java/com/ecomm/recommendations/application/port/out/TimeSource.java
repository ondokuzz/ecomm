package com.ecomm.recommendations.application.port.out;

import java.time.Instant;

public interface TimeSource {

  Instant now();
}
