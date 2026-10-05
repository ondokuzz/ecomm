package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.application.port.in.ReadStockMovementsUseCase;
import com.ecomm.inventory.application.port.in.ReadStockUseCase;
import com.ecomm.inventory.application.port.in.RemoveStockUseCase;
import com.ecomm.inventory.application.port.in.SetOnHandUseCase;
import com.ecomm.inventory.domain.InvalidStockRequestException;
import com.ecomm.inventory.domain.OnHandBelowReservedException;
import com.ecomm.inventory.domain.StockReservedException;
import com.ecomm.inventory.domain.UnknownVariantException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anyone may read stock (see {@code public-read-paths}); only Staff may set how many units are on
 * hand, stop stocking a Variant, or read its Stock movements. Checkout takes Stock through
 * Reservations ({@link ReservationController}).
 */
@RestController
@RequestMapping("/stock")
class StockController {

  private static final Logger log = LoggerFactory.getLogger(StockController.class);

  static final int MAX_PAGE_SIZE = 100;

  private final ReadStockUseCase read;
  private final ReadStockMovementsUseCase readMovements;
  private final SetOnHandUseCase setOnHand;
  private final RemoveStockUseCase remove;

  StockController(
      ReadStockUseCase read,
      ReadStockMovementsUseCase readMovements,
      SetOnHandUseCase setOnHand,
      RemoveStockUseCase remove) {
    this.read = read;
    this.readMovements = readMovements;
    this.setOnHand = setOnHand;
    this.remove = remove;
  }

  @GetMapping("/{variantId}")
  StockResponse stock(@PathVariable String variantId) {
    return read.stock(variantId)
        .map(StockResponse::of)
        .orElseThrow(() -> new UnknownVariantException(List.of(variantId)));
  }

  /**
   * A page of the Variant's Stock movements, newest first, and whether its On-hand equals their
   * sum: {@code page} counts from 0, and {@code size} is 1 to {@value #MAX_PAGE_SIZE}. 404 when
   * Inventory doesn't stock the Variant.
   */
  @GetMapping("/{variantId}/movements")
  @PreAuthorize("hasRole('STAFF')")
  StockLedgerResponse movements(
      @PathVariable String variantId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0) {
      throw new InvalidPageException("page counts from 0");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidPageException("size is 1 to " + MAX_PAGE_SIZE);
    }
    return readMovements
        .movements(variantId, page, size)
        .map(StockLedgerResponse::of)
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
    var result = setOnHand.setOnHand(variantId, request.toOnHand(), request.toReason());
    log.info("Set on-hand Stock of {} to {}", variantId, result.stock().onHand());
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(StockResponse.of(result.stock()));
  }

  /** 204, 404 when Inventory doesn't stock the Variant, 409 while Reservations hold some of it. */
  @DeleteMapping("/{variantId}")
  @PreAuthorize("hasRole('STAFF')")
  ResponseEntity<Void> removeStock(@PathVariable String variantId) {
    if (!remove.removeStock(variantId)) {
      throw new UnknownVariantException(List.of(variantId));
    }
    log.info("Removed the Stock of {}", variantId);
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(StockReservedException.class)
  ProblemDetail reserved(StockReservedException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("reserved", e.reserved());
    return problem;
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

  @ExceptionHandler(InvalidPageException.class)
  ProblemDetail invalidPage(InvalidPageException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
