package com.ecomm.catalog.adapter.out.clock;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The one clock Catalog reads: for when an outbox event occurred, and when Kafka took it. */
@Configuration
class ClockConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
