package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.ecomm.checkoutpricing.application.port.out.TaxCalculator;
import com.ecomm.commons.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The tax from the {@code TaxCalculator} goes on the Order, and the Payment is authorized for the
 * Order's total, so the two always match.
 */
class TaxApiTest extends CheckoutApiTest {

  @TestConfiguration
  static class TwentyPercentTax {

    @Bean
    @Primary
    TaxCalculator twentyPercentTax() {
      return cart -> new Money(cart.subtotal().amountMinor() / 5, cart.subtotal().currency());
    }
  }

  @Test
  void theTaxIsSentWithTheOrder() {
    stubSuccessfulCheckout();

    checkout().expectStatus().isOk();

    // 2 × 799.00, and 20% of that.
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/orders"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "lines": [
                      {"variantId": "PHN-PIXEL-9", "quantity": 2,
                       "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}
                    ],
                     "tax": {"amountMinor": 31960, "currency": "EUR"}}
                    """)));
  }

  @Test
  void thePaymentIsForTheOrdersTotal() {
    stubSuccessfulCheckout();
    // Order Management's total for those lines and that tax: 1598.00 + 319.60.
    DOWNSTREAM.stubFor(post("/orders").willReturn(placedOrder(191760)));

    checkout().expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/payments"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "orderId": "%s",
                     "amount": {"amountMinor": 191760, "currency": "EUR"}}
                    """
                        .formatted(ORDER_ID))));
  }
}
