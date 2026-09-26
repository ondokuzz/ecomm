package com.ecomm.cart;

import com.ecomm.cart.application.CartService;
import com.ecomm.cart.application.port.out.CartRepository;
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
  CartService cartService(CartRepository carts) {
    return new CartService(carts);
  }
}
