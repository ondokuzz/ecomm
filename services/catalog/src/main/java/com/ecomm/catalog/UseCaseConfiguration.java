package com.ecomm.catalog;

import com.ecomm.catalog.application.CatalogService;
import com.ecomm.catalog.application.CategoryService;
import com.ecomm.catalog.application.port.out.CategoryRepository;
import com.ecomm.catalog.application.port.out.ProductRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  /** Serves browsing, Staff changes to Products, and seeding. */
  @Bean
  CatalogService catalogService(ProductRepository products, CategoryRepository categories) {
    return new CatalogService(products, categories);
  }

  /** Serves Staff changes to Categories. */
  @Bean
  CategoryService categoryService(CategoryRepository categories, ProductRepository products) {
    return new CategoryService(categories, products);
  }
}
