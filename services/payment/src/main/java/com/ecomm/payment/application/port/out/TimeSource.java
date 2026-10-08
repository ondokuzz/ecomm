package com.ecomm.payment.application.port.out;

import java.time.Instant;

public interface TimeSource {

  Instant now();
}
