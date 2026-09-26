package com.ecomm.inventory;

import com.ecomm.inventory.application.InventoryService;
import com.ecomm.inventory.application.port.out.StockRepository;
import com.ecomm.inventory.application.port.out.Transactions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  /** Serves every Inventory use case: reading and decrementing stock. */
  @Bean
  InventoryService inventoryService(StockRepository stock, Transactions transactions) {
    return new InventoryService(stock, transactions);
  }
}
