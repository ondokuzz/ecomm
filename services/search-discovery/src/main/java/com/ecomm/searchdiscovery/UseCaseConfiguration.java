package com.ecomm.searchdiscovery;

import com.ecomm.searchdiscovery.application.ProjectionService;
import com.ecomm.searchdiscovery.application.SearchService;
import com.ecomm.searchdiscovery.application.port.in.ProjectionUseCase;
import com.ecomm.searchdiscovery.application.port.in.SearchUseCase;
import com.ecomm.searchdiscovery.application.port.out.CategoryStore;
import com.ecomm.searchdiscovery.application.port.out.ProductStore;
import com.ecomm.searchdiscovery.application.port.out.StockStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free use cases as beans, so {@code application} carries no Spring. */
@Configuration
class UseCaseConfiguration {

  @Bean
  SearchUseCase searchUseCase(ProductStore products, CategoryStore categories) {
    return new SearchService(products, categories);
  }

  @Bean
  ProjectionUseCase projectionUseCase(
      ProductStore products, CategoryStore categories, StockStore stock) {
    return new ProjectionService(products, categories, stock);
  }
}
