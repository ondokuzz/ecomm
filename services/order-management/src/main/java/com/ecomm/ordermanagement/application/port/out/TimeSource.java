package com.ecomm.ordermanagement.application.port.out;

import java.time.Instant;

public interface TimeSource {

  Instant now();
}
