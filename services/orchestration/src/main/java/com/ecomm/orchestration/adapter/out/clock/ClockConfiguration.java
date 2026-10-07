package com.ecomm.orchestration.adapter.out.clock;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock that decides when Orchestration's cached service token needs replacing. Workflows never
 * read it: they keep Temporal's own time.
 */
@Configuration
class ClockConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
