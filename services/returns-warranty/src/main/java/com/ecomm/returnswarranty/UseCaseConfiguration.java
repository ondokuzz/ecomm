package com.ecomm.returnswarranty;

import com.ecomm.returnswarranty.application.ProjectionService;
import com.ecomm.returnswarranty.application.RmaService;
import com.ecomm.returnswarranty.application.port.in.ProjectionUseCase;
import com.ecomm.returnswarranty.application.port.in.RmaUseCase;
import com.ecomm.returnswarranty.application.port.out.ProductStore;
import com.ecomm.returnswarranty.application.port.out.RmaRepository;
import com.ecomm.returnswarranty.application.port.out.ShipmentStore;
import com.ecomm.returnswarranty.application.port.out.TimeSource;
import com.ecomm.returnswarranty.application.port.out.Transactions;
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
      ShipmentStore shipments, ProductStore products, Transactions transactions) {
    return new ProjectionService(shipments, products, transactions);
  }

  /** RMAs, checked against the store's Return Period. */
  @Bean
  RmaUseCase rmaUseCase(
      ShipmentStore shipments,
      ProductStore products,
      RmaRepository rmas,
      Transactions transactions,
      TimeSource time,
      @Value("${ecomm.returns-warranty.return-period}") Duration returnPeriod) {
    return new RmaService(shipments, products, rmas, transactions, time, returnPeriod);
  }
}
