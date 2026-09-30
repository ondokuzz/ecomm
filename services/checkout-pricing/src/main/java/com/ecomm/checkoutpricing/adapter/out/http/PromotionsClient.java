package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.in.DownstreamFailureException;
import com.ecomm.checkoutpricing.application.port.out.PromotionsPort;
import com.ecomm.checkoutpricing.domain.CouponNotApplicableException;
import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.commons.money.Money;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Promotions' internal {@code POST /discounts/evaluate}, called with Checkout's token. A Coupon
 * that doesn't apply is a 422 whose problem detail says why in its {@code reason}.
 */
@Component
class PromotionsClient implements PromotionsPort {

  private final RestClient http;

  PromotionsClient(@Qualifier("promotionsRestClient") RestClient http) {
    this.http = http;
  }

  private record EvaluateBody(String couponCode, Amount subtotal) {}

  private record Amount(long amountMinor, String currency) {

    Money toMoney() {
      return Money.of(amountMinor, currency);
    }
  }

  private record DiscountBody(String couponCode, Amount discount) {}

  @Override
  public Discount evaluate(String couponCode, Money subtotal) {
    var body =
        new EvaluateBody(
            couponCode, new Amount(subtotal.amountMinor(), subtotal.currency().getCurrencyCode()));
    var answer =
        Downstream.call(
            "Promotions",
            () -> {
              try {
                return http.post()
                    .uri("/discounts/evaluate")
                    .body(body)
                    .retrieve()
                    .body(DiscountBody.class);
              } catch (HttpClientErrorException.UnprocessableContent e) {
                throw new CouponNotApplicableException(reason(e));
              }
            });
    if (answer == null || answer.couponCode() == null || answer.discount() == null) {
      throw new DownstreamFailureException(
          "Promotions evaluated " + couponCode + " to nothing", null);
    }
    var discount = answer.discount().toMoney();
    if (!discount.currency().equals(subtotal.currency())
        || discount.amountMinor() < 0
        || discount.amountMinor() > subtotal.amountMinor()) {
      throw new DownstreamFailureException(
          "Promotions evaluated " + couponCode + " to " + discount + " on " + subtotal, null);
    }
    return new Discount(answer.couponCode(), discount);
  }

  /** The {@code reason} of Promotions' problem detail. */
  private static String reason(HttpClientErrorException error) {
    try {
      var problem = error.getResponseBodyAs(Map.class);
      if (problem != null && problem.get("reason") instanceof String reason && !reason.isBlank()) {
        return reason;
      }
    } catch (RuntimeException ignored) {
      // An unreadable body gives no reason, and falls through.
    }
    throw new DownstreamFailureException("Promotions rejected a Coupon without a reason", error);
  }
}
