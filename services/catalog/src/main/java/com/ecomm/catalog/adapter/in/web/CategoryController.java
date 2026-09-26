package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.application.port.in.BrowseCatalogUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CategoryController {

  private final BrowseCatalogUseCase browse;

  CategoryController(BrowseCatalogUseCase browse) {
    this.browse = browse;
  }

  @GetMapping("/categories")
  List<CategoryResponse> categories() {
    return browse.categories().stream()
        .map(c -> new CategoryResponse(c.category(), c.productCount()))
        .toList();
  }

  record CategoryResponse(String category, long productCount) {}
}
