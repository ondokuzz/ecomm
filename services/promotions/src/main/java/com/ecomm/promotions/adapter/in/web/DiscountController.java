package com.ecomm.promotions.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.promotions.application.port.in.EvaluateDiscountUseCase;
import com.ecomm.promotions.domain.CouponNotApplicableException;
import com.ecomm.promotions.domain.Discount;
import com.ecomm.promotions.domain.InvalidPromotionException;
import java.util.Currency;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout evaluates every Discount a Checkout Session is due, the Coupon's included, with its own
 * {@code CHECKOUT} token. It is internal: the gateway never routes it, so nobody can probe Coupon
 * codes outside a checkout.
 */
@RestController
@RequestMapping("/discounts")
@PreAuthorize("hasRole('CHECKOUT')")
class DiscountController {

  private static final Logger log = LoggerFactory.getLogger(DiscountController.class);

  private final EvaluateDiscountUseCase discounts;

  DiscountController(EvaluateDiscountUseCase discounts) {
    this.discounts = discounts;
  }

  /**
   * 200 with every Discount the lines are due, in order; 422 with a {@code reason} when the Coupon
   * doesn't apply.
   */
  @PostMapping("/evaluate")
  DiscountResponse evaluate(@RequestBody EvaluateRequest request) {
    var lines = request.toLines();
    var discounts = this.discounts.evaluate(lines, request.toCouponCode());
    log.info(
        "{} Discounts take {} off",
        discounts.size(),
        total(discounts, lines.getFirst().unitPrice().currency()));
    return DiscountResponse.of(discounts);
  }

  private static Money total(List<Discount> discounts, Currency currency) {
    return new Money(discounts.stream().mapToLong(d -> d.amount().amountMinor()).sum(), currency);
  }

  @ExceptionHandler(CouponNotApplicableException.class)
  ProblemDetail notApplicable(CouponNotApplicableException e) {
    log.info("Coupon rejected: {}", e.rejection().reason());
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    problem.setProperty("reason", e.rejection().reason());
    return problem;
  }

  @ExceptionHandler(InvalidPromotionException.class)
  ProblemDetail invalid(InvalidPromotionException e) {
    return Problems.invalid(e);
  }
}
