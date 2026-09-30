package com.ecomm.checkoutpricing.adapter.out.clock;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one clock Checkout reads: for when a Checkout Session expires, and for when its cached
 * service token needs replacing.
 */
@Configuration
class ClockConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
