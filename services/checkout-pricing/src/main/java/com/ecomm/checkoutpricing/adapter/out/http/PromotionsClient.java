package com.ecomm.checkoutpricing.adapter.out.http;

import com.ecomm.checkoutpricing.application.port.in.DownstreamFailureException;
import com.ecomm.checkoutpricing.application.port.out.PromotionsPort;
import com.ecomm.checkoutpricing.domain.CouponNotApplicableException;
import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.checkoutpricing.domain.PricedCart;
import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.commons.money.Money;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Promotions' internal {@code POST /discounts/evaluate}, called with Checkout's token. A Coupon
 * that doesn't apply is a 422 whose problem detail says why in its {@code reason}. An answer that
 * couldn't be right, such as Discounts worth more than the lines or a Coupon's Discount that wasn't
 * asked for, is a downstream failure, never kept.
 */
@Component
class PromotionsClient implements PromotionsPort {

  private final RestClient http;

  PromotionsClient(@Qualifier("promotionsRestClient") RestClient http) {
    this.http = http;
  }

  /** {@code couponCode} is left out when the session has none. */
  private record EvaluateBody(
      List<LineBody> lines, @JsonInclude(JsonInclude.Include.NON_NULL) String couponCode) {}

  private record LineBody(
      String variantId, String sku, String category, int quantity, Amount unitPrice) {}

  private record Amount(long amountMinor, String currency) {

    static Amount of(Money money) {
      return new Amount(money.amountMinor(), money.currency().getCurrencyCode());
    }
  }

  private record AnswerBody(List<DiscountBody> discounts) {}

  private record DiscountBody(
      String source, String couponCode, String campaignId, String campaignName, Amount amount) {}

  @Override
  public List<Discount> evaluate(List<PricedLine> lines, Optional<String> couponCode) {
    var body =
        new EvaluateBody(
            lines.stream()
                .map(
                    l ->
                        new LineBody(
                            l.variantId(),
                            l.sku(),
                            l.category(),
                            l.quantity(),
                            Amount.of(l.unitPrice())))
                .toList(),
            couponCode.orElse(null));
    var answer =
        Downstream.call(
            "Promotions",
            () -> {
              try {
                return http.post()
                    .uri("/discounts/evaluate")
                    .body(body)
                    .retrieve()
                    .body(AnswerBody.class);
              } catch (HttpClientErrorException.UnprocessableContent e) {
                throw new CouponNotApplicableException(reason(e));
              }
            });
    if (answer == null || answer.discounts() == null) {
      throw new DownstreamFailureException("Promotions answered no Discounts", null);
    }
    return checked(answer.discounts(), lines, couponCode.isPresent());
  }

  /**
   * The Discounts, once each is seen to be a Campaign's or a Coupon's with what names it, in the
   * lines' currency, and not negative; together no more than the lines; and with a Coupon's last
   * exactly when one was asked about.
   */
  private static List<Discount> checked(
      List<DiscountBody> answered, List<PricedLine> lines, boolean couponAsked) {
    var subtotal = new PricedCart(lines).subtotal();
    var currency = subtotal.currency();
    var discounts = new ArrayList<Discount>();
    long taken = 0;
    for (var d : answered) {
      if (d == null || d.amount() == null || d.amount().currency() == null) {
        throw invalid("a Discount without an amount");
      }
      Money amount;
      try {
        amount = Money.of(d.amount().amountMinor(), d.amount().currency());
      } catch (IllegalArgumentException e) {
        throw invalid("a Discount in an unknown currency " + d.amount().currency());
      }
      if (!amount.currency().equals(currency) || amount.amountMinor() < 0) {
        throw invalid("a Discount of " + amount + " on lines in " + currency);
      }
      taken = Math.addExact(taken, amount.amountMinor());
      if ("CAMPAIGN".equals(d.source()) && present(d.campaignId()) && present(d.campaignName())) {
        discounts.add(Discount.campaign(d.campaignId(), d.campaignName(), amount));
      } else if ("COUPON".equals(d.source()) && present(d.couponCode())) {
        discounts.add(Discount.coupon(d.couponCode(), amount));
      } else {
        throw invalid("a Discount that names neither a Campaign nor a Coupon");
      }
    }
    if (taken > subtotal.amountMinor()) {
      throw invalid("Discounts of " + taken + " on lines of " + subtotal);
    }
    var coupons = discounts.stream().filter(d -> d.source() == Discount.Source.COUPON).count();
    var couponLast = !discounts.isEmpty() && discounts.getLast().source() == Discount.Source.COUPON;
    if (couponAsked ? coupons != 1 || !couponLast : coupons != 0) {
      throw invalid(couponAsked ? "no Coupon's Discount, last" : "a Coupon's Discount unasked");
    }
    return List.copyOf(discounts);
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static DownstreamFailureException invalid(String what) {
    return new DownstreamFailureException("Promotions answered " + what, null);
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
