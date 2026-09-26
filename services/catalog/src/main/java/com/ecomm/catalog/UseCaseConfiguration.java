package com.ecomm.catalog;

import com.ecomm.catalog.application.CatalogService;
import com.ecomm.catalog.application.port.out.ProductRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  /** Serves every Catalog use case: browsing, Staff changes and seeding. */
  @Bean
  CatalogService catalogService(ProductRepository products) {
    return new CatalogService(products);
  }
}
