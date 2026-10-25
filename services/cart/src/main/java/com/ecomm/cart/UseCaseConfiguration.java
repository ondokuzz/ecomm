package com.ecomm.cart;

import com.ecomm.cart.application.AbandonmentService;
import com.ecomm.cart.application.CartService;
import com.ecomm.cart.application.port.out.AbandonedCartCounter;
import com.ecomm.cart.application.port.out.AbandonedCartPublisher;
import com.ecomm.cart.application.port.out.CartRepository;
import com.ecomm.cart.application.port.out.SweepLock;
import com.ecomm.cart.application.port.out.TimeSource;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  /** Serves every Cart use case. */
  @Bean
  CartService cartService(CartRepository carts, TimeSource time) {
    return new CartService(carts, time);
  }

  /** Detects Abandoned Carts: idle for {@code ecomm.cart.abandoned-after}, 24 hours by default. */
  @Bean
  AbandonmentService abandonmentService(
      CartRepository carts,
      AbandonedCartPublisher events,
      AbandonedCartCounter counter,
      SweepLock lock,
      TimeSource time,
      @Value("${ecomm.cart.abandoned-after:24h}") Duration abandonedAfter) {
    return new AbandonmentService(carts, events, counter, lock, time, abandonedAfter);
  }
}
