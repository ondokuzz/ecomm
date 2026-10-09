package com.ecomm.checkoutpricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.checkoutpricing.application.port.out.TaxCalculator;
import com.ecomm.commons.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The tax from the {@code TaxCalculator} is worked out when the Checkout Session starts, and again
 * on the discounted subtotal when a Coupon is applied or removed. It is shown on the session and
 * sent with the Order; the Payment is authorized for the Order's total, so the two always match.
 */
class TaxApiTest extends CheckoutApiTest {

  @TestConfiguration
  static class TwentyPercentTax {

    @Bean
    @Primary
    TaxCalculator twentyPercentTax() {
      return (cart, discount) ->
          new Money(
              (cart.subtotal().amountMinor() - discount.amountMinor()) / 5,
              cart.subtotal().currency());
    }
  }

  @Test
  void theSessionShowsTheTaxAndTheTotalWithIt() {
    stubSuccessfulCheckout();

    // 2 × 799.00, and 20% of that.
    startSession()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.subtotal.amountMinor")
        .isEqualTo(159800)
        .jsonPath("$.tax.amountMinor")
        .isEqualTo(31960)
        .jsonPath("$.total.amountMinor")
        .isEqualTo(191760);
  }

  @Test
  void aCouponIsTakenOffBeforeTheTax() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);
    var sessionId = startedSessionId();

    // 1598.00 less 159.80 is 1438.20; 20% of that is 287.64.
    applyCoupon(sessionId, "WELCOME10")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.tax.amountMinor")
        .isEqualTo(28764)
        .jsonPath("$.total.amountMinor")
        .isEqualTo(172584);

    removeCoupon(sessionId)
        .expectBody()
        .jsonPath("$.tax.amountMinor")
        .isEqualTo(31960)
        .jsonPath("$.total.amountMinor")
        .isEqualTo(191760);
  }

  @Test
  void theSessionIsPaidWithItsTax() {
    stubSuccessfulCheckout();

    checkout().expectStatus().isOk();

    // 20% of 2 × 799.00.
    assertThat(saga.started().getFirst().session().tax()).isEqualTo(Money.of(31960, "EUR"));
  }
}
