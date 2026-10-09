package com.ecomm.checkoutpricing.adapter.in.web;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal: the checkout Saga ends a named Customer's Checkout Session once its Order is paid, with
 * Orchestration's own {@code ORCHESTRATION} token, naming the Customer in the body (ADR 0002). The
 * gateway doesn't route it.
 */
@RestController
@RequestMapping("/checkout/sessions")
@PreAuthorize("hasRole('ORCHESTRATION')")
class EndSessionController {

  private static final Logger log = LoggerFactory.getLogger(EndSessionController.class);

  private final CheckoutUseCase checkout;

  EndSessionController(CheckoutUseCase checkout) {
    this.checkout = checkout;
  }

  /**
   * 204 whether or not the session still exists; it ends only while the Customer's pointer still
   * names it, so a repeat, or a session the Customer has since replaced, changes nothing.
   */
  @PostMapping("/{id}/end")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void end(@PathVariable String id, @RequestBody EndSessionRequest request) {
    checkout.end(request.customer(), id);
    log.info("Ended Checkout Session {} for the checkout Saga, if it was still current", id);
  }

  @ExceptionHandler(InvalidEndSessionRequestException.class)
  ProblemDetail invalid(InvalidEndSessionRequestException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
