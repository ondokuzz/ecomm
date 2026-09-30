package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import org.junit.jupiter.api.Test;

/**
 * A Checkout Session prices every line at its Variant's current Price in Catalog when it starts,
 * whatever the Cart says, and the Order is placed at those Prices.
 */
class RepricingApiTest extends CheckoutApiTest {

  @Test
  void theOrderIsPlacedAtCatalogPricesNotTheCartsOwn() {
    stubSuccessfulCheckout();
    stubCart(
        """
        {"variantId": "PHN-PIXEL-9", "quantity": 2, "unitPrice": {"amountMinor": 1, "currency": "EUR"}},
        {"variantId": "AUD-SONY-XM5", "quantity": 1, "price": {"amountMinor": 1, "currency": "EUR"}}
        """);
    stubVariant("PHN-PIXEL-9", 79900);
    stubVariant("AUD-SONY-XM5", 34900);

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

  @Test
  void aVariantIsPricedAtItsOwnPriceNotItsProducts() {
    stubSuccessfulCheckout();
    stubCart("{\"variantId\": \"PHN-PIXEL-9-OBSIDIAN-256\", \"quantity\": 1}");
    stubVariant("PHN-PIXEL-9-OBSIDIAN-256", "PHN-PIXEL-9", 89900);

    checkout().expectStatus().isOk();

    DOWNSTREAM.verify(getRequestedFor(urlEqualTo("/variants/PHN-PIXEL-9-OBSIDIAN-256")));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/orders"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "lines": [
                      {"variantId": "PHN-PIXEL-9-OBSIDIAN-256", "quantity": 1,
                       "unitPrice": {"amountMinor": 89900, "currency": "EUR"}}
                    ],
                     "tax": {"amountMinor": 0, "currency": "EUR"}}
                    """)));
  }
}
