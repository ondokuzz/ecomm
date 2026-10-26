package com.ecomm.recommendations;

import com.ecomm.recommendations.application.AlsoBoughtService;
import com.ecomm.recommendations.application.ProjectionService;
import com.ecomm.recommendations.application.port.in.AlsoBoughtUseCase;
import com.ecomm.recommendations.application.port.in.ProjectionUseCase;
import com.ecomm.recommendations.application.port.out.Candidates;
import com.ecomm.recommendations.application.port.out.CategoryStore;
import com.ecomm.recommendations.application.port.out.OrderStore;
import com.ecomm.recommendations.application.port.out.ProductStore;
import com.ecomm.recommendations.application.port.out.StockStore;
import com.ecomm.recommendations.application.port.out.TimeSource;
import com.ecomm.recommendations.application.port.out.Transactions;
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

  @Bean
  ProjectionUseCase projectionUseCase(
      OrderStore orders,
      ProductStore products,
      CategoryStore categories,
      StockStore stock,
      Transactions transactions) {
    return new ProjectionService(orders, products, categories, stock, transactions);
  }

  /**
   * Only Orders placed within {@code ecomm.recommendations.lookback}, 180 days by default, count.
   */
  @Bean
  AlsoBoughtUseCase alsoBoughtUseCase(
      Candidates candidates,
      TimeSource time,
      @Value("${ecomm.recommendations.lookback:180d}") Duration lookback) {
    return new AlsoBoughtService(candidates, time, lookback);
  }
}
