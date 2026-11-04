package com.ecomm.supportassistant.application.port.out;

import java.time.Instant;

public interface TimeSource {

  Instant now();
}
