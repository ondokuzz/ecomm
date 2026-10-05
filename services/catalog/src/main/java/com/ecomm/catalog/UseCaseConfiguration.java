package com.ecomm.catalog;

import com.ecomm.catalog.application.CatalogService;
import com.ecomm.catalog.application.CategoryService;
import com.ecomm.catalog.application.port.out.CategoryRepository;
import com.ecomm.catalog.application.port.out.ProductRepository;
import com.ecomm.catalog.application.port.out.Transactions;
import com.ecomm.commons.events.IntegrationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  /** Serves browsing, Staff changes to Products, seeding, and the backfill of events. */
  @Bean
  CatalogService catalogService(
      ProductRepository products,
      CategoryRepository categories,
      CategoryService categoryService,
      Transactions transactions,
      IntegrationEventPublisher events) {
    return new CatalogService(products, categories, categoryService, transactions, events);
  }

  /** Serves Staff changes to Categories. */
  @Bean
  CategoryService categoryService(
      CategoryRepository categories,
      ProductRepository products,
      Transactions transactions,
      IntegrationEventPublisher events) {
    return new CategoryService(categories, products, transactions, events);
  }
}
