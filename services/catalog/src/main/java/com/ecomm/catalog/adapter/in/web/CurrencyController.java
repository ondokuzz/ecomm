package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.application.port.in.BrowseCatalogUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Anyone may read the currencies a Price can be in (see {@code public-read-paths}). */
@RestController
@RequestMapping("/currencies")
class CurrencyController {

  private final BrowseCatalogUseCase browse;

  CurrencyController(BrowseCatalogUseCase browse) {
    this.browse = browse;
  }

  @GetMapping
  List<CurrencyResponse> currencies() {
    return browse.currencies().stream().map(CurrencyResponse::of).toList();
  }
}
