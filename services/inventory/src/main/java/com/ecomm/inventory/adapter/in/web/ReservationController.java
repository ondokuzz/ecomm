package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.application.port.in.ReserveStockUseCase;
import com.ecomm.inventory.application.port.in.SettleReservationUseCase;
import com.ecomm.inventory.domain.InsufficientStockException;
import com.ecomm.inventory.domain.InvalidStockRequestException;
import com.ecomm.inventory.domain.Reservation;
import com.ecomm.inventory.domain.ReservationCommittedException;
import com.ecomm.inventory.domain.ReservationExpiredException;
import com.ecomm.inventory.domain.ReservationReleasedException;
import com.ecomm.inventory.domain.UnknownVariantException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal: Checkout, with its own {@code CHECKOUT} token, holds Stock for a Customer and releases
 * the Reservation; the checkout Saga commits it with Orchestration's {@code ORCHESTRATION} token.
 * Each names the Customer in the body (ADR 0002). A Reservation owned by anyone else is a 404.
 *
 * <p>Each endpoint checks its own role. A role on the class would guard the exception handlers too,
 * and turn a 404 or 409 for the other caller into a 500.
 */
@RestController
@RequestMapping("/reservations")
class ReservationController {

  private static final Logger log = LoggerFactory.getLogger(ReservationController.class);

  private final ReserveStockUseCase reserve;
  private final SettleReservationUseCase settle;

  ReservationController(ReserveStockUseCase reserve, SettleReservationUseCase settle) {
    this.reserve = reserve;
    this.settle = settle;
  }

  /** 201 with the new {@code ACTIVE} Reservation, and its URL in {@code Location}. */
  @PostMapping
  @PreAuthorize("hasRole('CHECKOUT')")
  ResponseEntity<ReservationResponse> reserve(@RequestBody ReservationRequest request) {
    var reservation =
        reserve.reserve(request.customerId(), request.toItems(), request.toExpiresAt());
    log.info(
        "Reserved Stock of {} Variants as {}",
        reservation.items().variantIds().size(),
        reservation.id());
    return ResponseEntity.created(URI.create("/reservations/" + reservation.id()))
        .body(ReservationResponse.of(reservation));
  }

  /**
   * 200 with the Reservation {@code COMMITTED}, its Stock taken off on-hand. Committing it again
   * changes nothing, so the command takes no {@code Idempotency-Key}.
   */
  @PostMapping("/{id}/commit")
  @PreAuthorize("hasRole('ORCHESTRATION')")
  ReservationResponse commit(@PathVariable String id, @RequestBody CustomerRequest request) {
    var reservation =
        owned(id, reservationId -> settle.commit(request.customerId(), reservationId));
    log.info("Committed Reservation {}", id);
    return reservation;
  }

  /** 200 with the Reservation {@code RELEASED}, its Stock available again. */
  @PostMapping("/{id}/release")
  @PreAuthorize("hasRole('CHECKOUT')")
  ReservationResponse release(@PathVariable String id, @RequestBody CustomerRequest request) {
    var reservation =
        owned(id, reservationId -> settle.release(request.customerId(), reservationId));
    log.info("Released Reservation {}", id);
    return reservation;
  }

  /** The Reservation {@code action} returns; a 404 if the ID isn't a UUID or it finds none. */
  private static ReservationResponse owned(
      String id, Function<UUID, Optional<Reservation>> action) {
    return parse(id)
        .flatMap(action)
        .map(ReservationResponse::of)
        .orElseThrow(() -> new ReservationNotFoundException(id));
  }

  private static Optional<UUID> parse(String id) {
    try {
      return Optional.of(UUID.fromString(id));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  @ExceptionHandler(ReservationNotFoundException.class)
  ProblemDetail notFound(ReservationNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(ReservationExpiredException.class)
  ProblemDetail expired(ReservationExpiredException e) {
    return conflict(e, "reservationExpired", e.reservationId());
  }

  @ExceptionHandler(ReservationReleasedException.class)
  ProblemDetail released(ReservationReleasedException e) {
    return conflict(e, "reservationReleased", e.reservationId());
  }

  @ExceptionHandler(ReservationCommittedException.class)
  ProblemDetail committed(ReservationCommittedException e) {
    return conflict(e, "reservationCommitted", e.reservationId());
  }

  private static ProblemDetail conflict(RuntimeException e, String property, UUID reservationId) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty(property, reservationId.toString());
    return problem;
  }

  @ExceptionHandler(UnknownVariantException.class)
  ProblemDetail unknownVariant(UnknownVariantException e) {
    return StockProblems.unknownVariant(e);
  }

  @ExceptionHandler(InsufficientStockException.class)
  ProblemDetail insufficientStock(InsufficientStockException e) {
    return StockProblems.insufficientStock(e);
  }

  @ExceptionHandler(InvalidStockRequestException.class)
  ProblemDetail invalid(InvalidStockRequestException e) {
    return StockProblems.invalid(e);
  }
}
