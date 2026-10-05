package com.ecomm.inventory;

import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.inventory.application.InventoryService;
import com.ecomm.inventory.application.port.out.ReservationRepository;
import com.ecomm.inventory.application.port.out.StockMovementRepository;
import com.ecomm.inventory.application.port.out.StockRepository;
import com.ecomm.inventory.application.port.out.TimeSource;
import com.ecomm.inventory.application.port.out.Transactions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  /** Serves every Inventory use case: Stock, its Reservations and its movements. */
  @Bean
  InventoryService inventoryService(
      StockRepository stock,
      ReservationRepository reservations,
      StockMovementRepository movements,
      Transactions transactions,
      IntegrationEventPublisher events,
      TimeSource time) {
    return new InventoryService(stock, reservations, movements, transactions, events, time);
  }
}
