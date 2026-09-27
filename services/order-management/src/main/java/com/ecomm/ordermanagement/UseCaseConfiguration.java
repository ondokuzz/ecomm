package com.ecomm.ordermanagement;

import com.ecomm.ordermanagement.application.OrderService;
import com.ecomm.ordermanagement.application.port.out.OrderRepository;
import com.ecomm.ordermanagement.application.port.out.TimeSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  /** Serves every Order use case. */
  @Bean
  OrderService orderService(OrderRepository orders, TimeSource time) {
    return new OrderService(orders, time);
  }
}
