package com.ecomm.fulfillmentshipping.application.port.out;

import java.time.Instant;

/** The current time, so that tests can fix it. */
public interface TimeSource {

  Instant now();
}
