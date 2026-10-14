package com.ecomm.returnswarranty.application.port.out;

import java.time.Instant;

public interface TimeSource {

  Instant now();
}
