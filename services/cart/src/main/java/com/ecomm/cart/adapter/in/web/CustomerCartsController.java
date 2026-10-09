package com.ecomm.cart.adapter.in.web;

import com.ecomm.cart.application.port.in.ClearCartUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal: the checkout Saga clears a named Customer's Cart once their Order is paid, with
 * Orchestration's own {@code ORCHESTRATION} token, naming the Customer in the body (ADR 0002). The
 * gateway doesn't route it.
 */
@RestController
@PreAuthorize("hasRole('ORCHESTRATION')")
@RequestMapping("/carts")
class CustomerCartsController {

  private static final Logger log = LoggerFactory.getLogger(CustomerCartsController.class);

  private final ClearCartUseCase clear;

  CustomerCartsController(ClearCartUseCase clear) {
    this.clear = clear;
  }

  /** 204 once the Cart is empty, whether or not it had anything in it. */
  @PostMapping("/clear")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void clear(@RequestBody ClearCartRequest request) {
    clear.clear(request.customer());
    log.info("Cleared a Customer's Cart for the checkout Saga");
  }

  @ExceptionHandler(InvalidClearRequestException.class)
  ProblemDetail invalid(InvalidClearRequestException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
