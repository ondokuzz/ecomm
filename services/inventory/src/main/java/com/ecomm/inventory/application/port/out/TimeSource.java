package com.ecomm.inventory.application.port.out;

import java.time.Instant;

public interface TimeSource {

  Instant now();
}
