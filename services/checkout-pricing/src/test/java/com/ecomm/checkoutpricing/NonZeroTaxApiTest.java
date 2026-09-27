package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.ecomm.checkoutpricing.application.port.out.TaxCalculator;
import com.ecomm.commons.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

/**
 * An Order can't carry tax yet (#14), so a non-zero tax would authorize a Payment larger than the
 * Order's total. Checkout refuses instead, before any Order exists.
 */
class NonZeroTaxApiTest extends CheckoutApiTest {

  @TestConfiguration
  static class TwentyPercentTax {

    @Bean
    @Primary
    TaxCalculator twentyPercentTax() {
      return cart -> new Money(cart.subtotal().amountMinor() / 5, cart.subtotal().currency());
    }
  }

  @Test
  void aNonZeroTaxRefusesCheckoutBeforeAnyOrderExists() {
    stubSuccessfulCheckout();

    checkout()
        .expectStatus()
        .is5xxServerError()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo("/orders")));
    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo("/payments")));
  }
}
