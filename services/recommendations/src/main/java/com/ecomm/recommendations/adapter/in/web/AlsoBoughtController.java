package com.ecomm.recommendations.adapter.in.web;

import com.ecomm.recommendations.application.port.in.AlsoBoughtUseCase;
import com.ecomm.recommendations.domain.InvalidQueryException;
import com.ecomm.recommendations.domain.Recommendation;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anyone may read what Customers also bought with a Product. */
@RestController
class AlsoBoughtController {

  record AlsoBoughtResponse(String sku, List<Item> items) {}

  /** A Product bought with it, and in how many counting Orders. */
  record Item(String sku, long orders) {

    static Item of(Recommendation recommendation) {
      return new Item(recommendation.sku(), recommendation.orders());
    }
  }

  private final AlsoBoughtUseCase alsoBought;

  AlsoBoughtController(AlsoBoughtUseCase alsoBought) {
    this.alsoBought = alsoBought;
  }

  /** Up to {@code limit} Products, from 1 to 12, most bought together first. */
  @GetMapping("/products/{sku}/also-bought")
  AlsoBoughtResponse alsoBought(
      @PathVariable String sku,
      @RequestParam(defaultValue = "" + AlsoBoughtUseCase.DEFAULT_LIMIT) int limit) {
    return new AlsoBoughtResponse(
        sku, alsoBought.alsoBought(sku, limit).stream().map(Item::of).toList());
  }

  @ExceptionHandler(InvalidQueryException.class)
  ProblemDetail invalid(InvalidQueryException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
