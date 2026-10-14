package com.ecomm.fulfillmentshipping;

import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.fulfillmentshipping.application.ProductProjectionService;
import com.ecomm.fulfillmentshipping.application.ShipmentService;
import com.ecomm.fulfillmentshipping.application.ShippingQuoteService;
import com.ecomm.fulfillmentshipping.application.WarehouseService;
import com.ecomm.fulfillmentshipping.application.port.in.ListWarehousesUseCase;
import com.ecomm.fulfillmentshipping.application.port.in.ProjectProductUseCase;
import com.ecomm.fulfillmentshipping.application.port.in.QuoteShippingUseCase;
import com.ecomm.fulfillmentshipping.application.port.out.ProductStore;
import com.ecomm.fulfillmentshipping.application.port.out.ShipmentRepository;
import com.ecomm.fulfillmentshipping.application.port.out.ShippingProvider;
import com.ecomm.fulfillmentshipping.application.port.out.TimeSource;
import com.ecomm.fulfillmentshipping.application.port.out.Transactions;
import com.ecomm.fulfillmentshipping.domain.Warehouses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  @Bean
  ListWarehousesUseCase listWarehousesUseCase(Warehouses warehouses) {
    return new WarehouseService(warehouses);
  }

  @Bean
  QuoteShippingUseCase quoteShippingUseCase(Warehouses warehouses, ShippingProvider carrier) {
    return new ShippingQuoteService(warehouses, carrier);
  }

  /** Creating, moving on, reading and tracking Shipments, and the Warehouse check at startup. */
  @Bean
  ShipmentService shipmentService(
      ShipmentRepository shipments,
      ProductStore products,
      Warehouses warehouses,
      ShippingProvider carrier,
      IntegrationEventPublisher events,
      Transactions transactions,
      TimeSource time) {
    return new ShipmentService(
        shipments, products, warehouses, carrier, events, transactions, time);
  }

  @Bean
  ProjectProductUseCase projectProductUseCase(ProductStore products, Transactions transactions) {
    return new ProductProjectionService(products, transactions);
  }
}
