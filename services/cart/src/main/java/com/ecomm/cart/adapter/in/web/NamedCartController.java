package com.ecomm.cart.adapter.in.web;

import com.ecomm.cart.application.port.in.ReadNamedCartUseCase;
import com.ecomm.cart.domain.ChangedCart;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal: Notifications reads a named Customer's Cart with its own {@code NOTIFICATIONS} token,
 * before it sends a Cart reminder, to check the Cart is the one abandoned. The gateway doesn't
 * route it (ADR 0010).
 */
@RestController
@PreAuthorize("hasRole('NOTIFICATIONS')")
class NamedCartController {

  /** The Cart's lines, when it last changed, and that change in epoch milliseconds. */
  record NamedCartResponse(List<CartResponse.Item> items, Instant lastChangedAt, long version) {

    static NamedCartResponse of(ChangedCart changed) {
      return new NamedCartResponse(
          CartResponse.of(changed.cart()).items(), changed.lastChangedAt(), changed.version());
    }
  }

  private final ReadNamedCartUseCase carts;

  NamedCartController(ReadNamedCartUseCase carts) {
    this.carts = carts;
  }

  /** 404 when the Customer has no Cart, or it is empty. */
  @GetMapping("/internal/carts/{customerId}")
  NamedCartResponse cart(@PathVariable String customerId) {
    return carts.cartOf(customerId).map(NamedCartResponse::of).orElseThrow(NoCartException::new);
  }

  @ExceptionHandler(NoCartException.class)
  ProblemDetail noCart() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The Customer has no Cart");
  }

  private static class NoCartException extends RuntimeException {}
}
