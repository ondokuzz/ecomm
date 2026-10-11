package com.ecomm.recommendations;

import com.ecomm.recommendations.application.AlsoBoughtService;
import com.ecomm.recommendations.application.ProjectionService;
import com.ecomm.recommendations.application.port.in.AlsoBoughtUseCase;
import com.ecomm.recommendations.application.port.in.ProjectionUseCase;
import com.ecomm.recommendations.application.port.out.BoughtTogether;
import com.ecomm.recommendations.application.port.out.OrderStore;
import com.ecomm.recommendations.application.port.out.ProductStore;
import com.ecomm.recommendations.application.port.out.Transactions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  @Bean
  ProjectionUseCase projectionUseCase(
      OrderStore orders, ProductStore products, Transactions transactions) {
    return new ProjectionService(orders, products, transactions);
  }

  @Bean
  AlsoBoughtUseCase alsoBoughtUseCase(BoughtTogether boughtTogether) {
    return new AlsoBoughtService(boughtTogether);
  }
}
