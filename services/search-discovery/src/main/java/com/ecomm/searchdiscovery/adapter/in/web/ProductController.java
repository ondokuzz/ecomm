package com.ecomm.searchdiscovery.adapter.in.web;

import com.ecomm.searchdiscovery.application.port.in.ProductUseCase;
import com.ecomm.searchdiscovery.domain.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Anyone may read one Product (see {@code public-read-paths}), as for a search. */
@RestController
class ProductController {

  private final ProductUseCase products;

  ProductController(ProductUseCase products) {
    this.products = products;
  }

  @GetMapping("/products/{sku}")
  ProductResponse product(@PathVariable String sku) {
    return products
        .product(sku)
        .map(ProductResponse::of)
        .orElseThrow(() -> new ProductNotFoundException(sku));
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ProblemDetail notFound(ProductNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }
}
