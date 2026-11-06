package com.ecomm.supportassistant;

import com.ecomm.supportassistant.application.port.out.TimeSource;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Stands in for the clock: it stays where a test sets it, until the test moves it on. */
class SettableTimeSource implements TimeSource {

  static final Instant START = Instant.parse("2026-11-06T09:00:00Z");

  private Instant now = START;

  synchronized void reset() {
    now = START;
  }

  synchronized void advance(Duration by) {
    now = now.plus(by);
  }

  @Override
  public synchronized Instant now() {
    return now;
  }

  @TestConfiguration
  static class Clock {

    @Bean
    @Primary
    SettableTimeSource settableTimeSource() {
      return new SettableTimeSource();
    }
  }
}
