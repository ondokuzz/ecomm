package com.ecomm.notifications.application.port.out;

import java.time.Instant;

public interface TimeSource {

  Instant now();
}
