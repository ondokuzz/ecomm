package com.ecomm.checkoutpricing.adapter.in.web;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUseCase;
import com.ecomm.checkoutpricing.application.port.in.DownstreamFailureException;
import com.ecomm.checkoutpricing.application.port.in.ServiceTokenUnavailableException;
import com.ecomm.checkoutpricing.domain.Customer;
import com.ecomm.checkoutpricing.domain.EmptyCartException;
import com.ecomm.checkoutpricing.domain.MixedCurrencyException;
import com.ecomm.checkoutpricing.domain.OutOfStockException;
import com.ecomm.checkoutpricing.domain.UnknownVariantsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling Customer checks out their own Cart. Needs a {@code CUSTOMER} token, which is also the
 * one Checkout forwards to Cart.
 */
@RestController
@RequestMapping("/checkout")
class CheckoutController {

  private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

  private final CheckoutUseCase checkout;

  CheckoutController(CheckoutUseCase checkout) {
    this.checkout = checkout;
  }

  @PostMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  CheckoutResponse checkout(@AuthenticationPrincipal Jwt token) {
    return CheckoutResponse.of(
        checkout.checkout(new Customer(token.getSubject(), token.getTokenValue())));
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
