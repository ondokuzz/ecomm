package com.ecomm.fulfillmentshipping;

import com.ecomm.fulfillmentshipping.application.ShippingQuoteService;
import com.ecomm.fulfillmentshipping.application.WarehouseService;
import com.ecomm.fulfillmentshipping.application.port.in.ListWarehousesUseCase;
import com.ecomm.fulfillmentshipping.application.port.in.QuoteShippingUseCase;
import com.ecomm.fulfillmentshipping.application.port.out.ShippingProvider;
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
}
