package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.application.port.in.BrowseCatalogUseCase;
import com.ecomm.catalog.application.port.in.ManageProductsUseCase;
import com.ecomm.catalog.domain.InvalidProductException;
import com.ecomm.catalog.domain.ProductAlreadyExistsException;
import com.ecomm.catalog.domain.ProductNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Anyone may read Products (see {@code public-read-paths}); only Staff may change them. */
@RestController
@RequestMapping("/products")
class ProductController {

  private final BrowseCatalogUseCase browse;
  private final ManageProductsUseCase manage;

  ProductController(BrowseCatalogUseCase browse, ManageProductsUseCase manage) {
    this.browse = browse;
    this.manage = manage;
  }

  @GetMapping
  List<ProductResponse> products(@RequestParam Optional<String> category) {
    // "?category=" means no filter, not the empty category.
    var filter = category.filter(c -> !c.isBlank());
    return browse.products(filter).stream().map(ProductResponse::of).toList();
  }

  @GetMapping("/{sku}")
  ProductResponse product(@PathVariable String sku) {
    return browse
        .product(sku)
        .map(ProductResponse::of)
        .orElseThrow(() -> new ProductNotFoundException(sku));
  }

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  ResponseEntity<ProductResponse> create(@RequestBody ProductRequest request) {
    var product = manage.create(request.toProduct());
    var location =
        UriComponentsBuilder.fromPath("/products/{sku}")
            .buildAndExpand(product.sku())
            .encode()
            .toUri();
    return ResponseEntity.created(location).body(ProductResponse.of(product));
  }

  @PutMapping("/{sku}")
  @PreAuthorize("hasRole('STAFF')")
  ProductResponse update(@PathVariable String sku, @RequestBody ProductRequest request) {
    return ProductResponse.of(manage.update(request.toProductWithSku(sku)));
  }

  @DeleteMapping("/{sku}")
  @PreAuthorize("hasRole('STAFF')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable String sku) {
    manage.delete(sku);
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ProblemDetail notFound(ProductNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(ProductAlreadyExistsException.class)
  ProblemDetail alreadyExists(ProductAlreadyExistsException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(InvalidProductException.class)
  ProblemDetail invalid(InvalidProductException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    if (!e.violations().isEmpty()) {
      problem.setProperty("errors", e.violations());
    }
    return problem;
  }
}
