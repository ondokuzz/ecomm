package com.ecomm.template.application.port.out;

import java.time.Instant;

public interface TimeSource {

  Instant now();
}
