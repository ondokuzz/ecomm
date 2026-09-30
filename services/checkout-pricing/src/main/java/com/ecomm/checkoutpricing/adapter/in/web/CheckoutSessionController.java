package com.ecomm.checkoutpricing.adapter.in.web;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUseCase;
import com.ecomm.checkoutpricing.application.port.in.DownstreamFailureException;
import com.ecomm.checkoutpricing.application.port.in.ServiceTokenUnavailableException;
import com.ecomm.checkoutpricing.domain.CheckoutSessionExpiredException;
import com.ecomm.checkoutpricing.domain.CheckoutSessionNotFoundException;
import com.ecomm.checkoutpricing.domain.EmptyCartException;
import com.ecomm.checkoutpricing.domain.MixedCurrencyException;
import com.ecomm.checkoutpricing.domain.OutOfStockException;
import com.ecomm.checkoutpricing.domain.UnknownVariantsException;
import com.ecomm.commons.security.CurrentCustomer;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling Customer checks out their own Cart, in two steps: a Checkout Session holds it, then
 * paying the session buys it. Needs a {@code CUSTOMER} token, which Checkout also forwards to Cart.
 */
@RestController
@RequestMapping("/checkout/sessions")
@PreAuthorize("hasRole('CUSTOMER')")
class CheckoutSessionController {

  private static final Logger log = LoggerFactory.getLogger(CheckoutSessionController.class);

  private final CheckoutUseCase checkout;

  CheckoutSessionController(CheckoutUseCase checkout) {
    this.checkout = checkout;
  }

  /** 201 with the new Checkout Session, replacing any the Customer had. */
  @PostMapping
  ResponseEntity<SessionResponse> start(CurrentCustomer customer) {
    var session = checkout.start(customer.id());
    log.info("Started Checkout Session {} until {}", session.id(), session.expiresAt());
    return ResponseEntity.created(URI.create("/checkout/sessions/" + session.id()))
        .body(SessionResponse.of(session));
  }

  /** The Customer's live Checkout Session, or a 404. */
  @GetMapping("/current")
  SessionResponse current(CurrentCustomer customer) {
    return checkout
        .current(customer.id())
        .map(SessionResponse::of)
        .orElseThrow(NoCurrentSessionException::new);
  }

  /** 200 with the paid Order's ID and Order Status; 410 once the session has expired. */
  @PostMapping("/{id}/pay")
  CheckoutResponse pay(@PathVariable String id, CurrentCustomer customer) {
    var result = checkout.pay(customer.id(), id);
    log.info("Paid Checkout Session {} as Order {}, {}", id, result.orderId(), result.status());
    return CheckoutResponse.of(result);
  }

  @ExceptionHandler(CheckoutSessionNotFoundException.class)
  ProblemDetail sessionNotFound(CheckoutSessionNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(CheckoutSessionExpiredException.class)
  ProblemDetail sessionExpired(CheckoutSessionExpiredException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, e.getMessage());
  }

  @ExceptionHandler(NoCurrentSessionException.class)
  ProblemDetail noCurrentSession(NoCurrentSessionException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(EmptyCartException.class)
  ProblemDetail emptyCart(EmptyCartException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(UnknownVariantsException.class)
  ProblemDetail unknownVariants(UnknownVariantsException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("unknownVariants", e.variantIds());
    return problem;
  }

  @ExceptionHandler(OutOfStockException.class)
  ProblemDetail outOfStock(OutOfStockException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("outOfStock", e.variantIds());
    return problem;
  }

  @ExceptionHandler(MixedCurrencyException.class)
  ProblemDetail mixedCurrency(MixedCurrencyException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(DownstreamFailureException.class)
  ProblemDetail downstreamFailure(DownstreamFailureException e) {
    log.warn("Checkout failed downstream", e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_GATEWAY, "Checkout could not complete; please try again.");
  }

  @ExceptionHandler(ServiceTokenUnavailableException.class)
  ProblemDetail serviceTokenUnavailable(ServiceTokenUnavailableException e) {
    log.warn("Checkout could not get its service token", e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "Checkout is unavailable right now; please try again.");
  }
}
