package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.application.port.in.BrowseCatalogUseCase;
import com.ecomm.catalog.domain.VariantNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Anyone may look up a Variant by its ID (see {@code public-read-paths}). */
@RestController
@RequestMapping("/variants")
class VariantController {

  private final BrowseCatalogUseCase browse;

  VariantController(BrowseCatalogUseCase browse) {
    this.browse = browse;
  }

  @GetMapping("/{variantId}")
  VariantLookupResponse variant(@PathVariable String variantId) {
    return browse
        .productWithVariant(variantId)
        .flatMap(p -> p.variant(variantId).map(v -> VariantLookupResponse.of(p, v)))
        .orElseThrow(() -> new VariantNotFoundException(variantId));
  }

  @ExceptionHandler(VariantNotFoundException.class)
  ProblemDetail notFound(VariantNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }
}
