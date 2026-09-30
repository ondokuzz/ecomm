package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.application.port.in.ReadStockUseCase;
import com.ecomm.inventory.application.port.in.SetOnHandUseCase;
import com.ecomm.inventory.domain.InvalidStockRequestException;
import com.ecomm.inventory.domain.OnHandBelowReservedException;
import com.ecomm.inventory.domain.UnknownVariantException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anyone may read stock (see {@code public-read-paths}); only Staff may set how many units are on
 * hand. Checkout takes Stock through Reservations ({@link ReservationController}).
 */
@RestController
@RequestMapping("/stock")
class StockController {

  private static final Logger log = LoggerFactory.getLogger(StockController.class);

  private final ReadStockUseCase read;
  private final SetOnHandUseCase setOnHand;

  StockController(ReadStockUseCase read, SetOnHandUseCase setOnHand) {
    this.read = read;
    this.setOnHand = setOnHand;
  }

  @GetMapping("/{variantId}")
  StockResponse stock(@PathVariable String variantId) {
    return read.stock(variantId)
        .map(StockResponse::of)
        .orElseThrow(() -> new UnknownVariantException(List.of(variantId)));
  }

  /**
   * 201 when this adds the Variant to Inventory, 200 when it changes one already stocked; 409 when
   * Reservations hold more than the new count.
   */
  @PutMapping("/{variantId}")
  @PreAuthorize("hasRole('STAFF')")
  ResponseEntity<StockResponse> setOnHand(
      @PathVariable String variantId, @RequestBody OnHandRequest request) {
    var result = setOnHand.setOnHand(variantId, request.toOnHand());
    log.info("Set on-hand Stock of {} to {}", variantId, result.stock().onHand());
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(StockResponse.of(result.stock()));
  }

  @ExceptionHandler(OnHandBelowReservedException.class)
  ProblemDetail belowReserved(OnHandBelowReservedException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("reserved", e.reserved());
    return problem;
  }

  @ExceptionHandler(UnknownVariantException.class)
  ProblemDetail unknownVariant(UnknownVariantException e) {
    return StockProblems.unknownVariant(e);
  }

  @ExceptionHandler(InvalidStockRequestException.class)
  ProblemDetail invalid(InvalidStockRequestException e) {
    return StockProblems.invalid(e);
  }
}
