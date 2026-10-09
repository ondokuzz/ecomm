package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.commons.money.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A Checkout Session prices every line at its Variant's current Price in Catalog when it starts,
 * whatever the Cart says, and is paid at those Prices.
 */
class RepricingApiTest extends CheckoutApiTest {

  @Test
  void theSessionIsPaidAtCatalogPricesNotTheCartsOwn() {
    stubSuccessfulCheckout();
    stubCart(
        """
        {"variantId": "PHN-PIXEL-9", "quantity": 2, "unitPrice": {"amountMinor": 1, "currency": "EUR"}},
        {"variantId": "AUD-SONY-XM5", "quantity": 1, "price": {"amountMinor": 1, "currency": "EUR"}}
        """);
    stubVariant("PHN-PIXEL-9", 79900);
    stubVariant("AUD-SONY-XM5", 34900);

    checkout().expectStatus().isOk();

    assertThat(paidLines())
        .containsExactly(
            new PricedLine("PHN-PIXEL-9", "PHN-PIXEL-9", "phones", 2, Money.of(79900, "EUR")),
            new PricedLine("AUD-SONY-XM5", "AUD-SONY-XM5", "phones", 1, Money.of(34900, "EUR")));
  }

  @Test
  void aVariantIsPricedAtItsOwnPriceNotItsProducts() {
    stubSuccessfulCheckout();
    stubCart("{\"variantId\": \"PHN-PIXEL-9-OBSIDIAN-256\", \"quantity\": 1}");
    stubVariant("PHN-PIXEL-9-OBSIDIAN-256", "PHN-PIXEL-9", 89900);

    checkout().expectStatus().isOk();

    DOWNSTREAM.verify(getRequestedFor(urlEqualTo("/variants/PHN-PIXEL-9-OBSIDIAN-256")));
    assertThat(paidLines())
        .containsExactly(
            new PricedLine(
                "PHN-PIXEL-9-OBSIDIAN-256", "PHN-PIXEL-9", "phones", 1, Money.of(89900, "EUR")));
  }

  /** The lines of the session the Saga was started with. */
  private List<PricedLine> paidLines() {
    return saga.started().getFirst().session().cart().lines();
  }
}
