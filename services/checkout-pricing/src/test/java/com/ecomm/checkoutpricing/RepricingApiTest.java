package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import org.junit.jupiter.api.Test;

/** Checkout prices every line at Catalog's current Price, whatever the Cart says. */
class RepricingApiTest extends CheckoutApiTest {

  @Test
  void theOrderIsPlacedAtCatalogPricesNotTheCartsOwn() {
    stubSuccessfulCheckout();
    stubCart(
        """
        {"variantId": "PHN-PIXEL-9", "quantity": 2, "unitPrice": {"amountMinor": 1, "currency": "EUR"}},
        {"variantId": "AUD-SONY-XM5", "quantity": 1, "price": {"amountMinor": 1, "currency": "EUR"}}
        """);
    stubProduct("PHN-PIXEL-9", 79900);
    stubProduct("AUD-SONY-XM5", 34900);

    checkout().expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/orders"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "lines": [
                      {"variantId": "PHN-PIXEL-9", "quantity": 2,
                       "unitPrice": {"amountMinor": 79900, "currency": "EUR"}},
                      {"variantId": "AUD-SONY-XM5", "quantity": 1,
                       "unitPrice": {"amountMinor": 34900, "currency": "EUR"}}
                    ],
                     "tax": {"amountMinor": 0, "currency": "EUR"}}
                    """)));
  }
}
