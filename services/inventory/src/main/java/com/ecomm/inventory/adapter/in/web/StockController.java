package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.application.port.in.DecrementStockUseCase;
import com.ecomm.inventory.application.port.in.ReadStockUseCase;
import com.ecomm.inventory.domain.InsufficientStockException;
import com.ecomm.inventory.domain.InvalidStockDecrementException;
import com.ecomm.inventory.domain.UnknownVariantException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anyone may read stock (see {@code public-read-paths}); only Checkout, with its own {@code
 * CHECKOUT} token, may decrement it.
 */
@RestController
@RequestMapping("/stock")
class StockController {

  private static final Logger log = LoggerFactory.getLogger(StockController.class);

  private final ReadStockUseCase read;
  private final DecrementStockUseCase decrement;

  StockController(ReadStockUseCase read, DecrementStockUseCase decrement) {
    this.read = read;
    this.decrement = decrement;
  }

  @GetMapping("/{variantId}")
  StockResponse stock(@PathVariable String variantId) {
    return read.stock(variantId)
        .map(StockResponse::of)
        .orElseThrow(() -> new UnknownVariantException(List.of(variantId)));
  }

  /** Returns the new stock of every Variant in the batch. */
  @PostMapping("/decrement")
  @PreAuthorize("hasRole('CHECKOUT')")
  List<StockResponse> decrement(@RequestBody DecrementRequest request) {
    var stock = decrement.decrement(request.toDecrement()).stream().map(StockResponse::of).toList();
    log.info("Decremented Stock of {} Variants", stock.size());
    return stock;
  }

  @ExceptionHandler(UnknownVariantException.class)
  ProblemDetail unknownVariant(UnknownVariantException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    problem.setProperty("unknownVariants", e.variantIds());
    return problem;
  }

  @ExceptionHandler(InsufficientStockException.class)
  ProblemDetail insufficientStock(InsufficientStockException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("insufficientStock", e.variantIds());
    return problem;
  }

  @ExceptionHandler(InvalidStockDecrementException.class)
  ProblemDetail invalid(InvalidStockDecrementException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
