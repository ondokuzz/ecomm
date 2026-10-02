package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Anyone may ask which currencies a Price can be in, with each one's minor unit, so clients read
 * and write Money as Catalog does rather than by their own currency data.
 */
class CurrenciesApiTest extends CatalogApiTest {

  record CurrencyView(String code, int minorDigits) {}

  @Test
  void anyoneListsTheCurrenciesAPriceCanBeInByCode() {
    var currencies = currencies();

    assertThat(currencies)
        .contains(
            new CurrencyView("EUR", 2),
            new CurrencyView("JPY", 0),
            new CurrencyView("BHD", 3),
            // Browsers' currency data says 0 for these; ISO 4217 says otherwise, and so does
            // Catalog.
            new CurrencyView("HUF", 2),
            new CurrencyView("IQD", 3))
        .isSortedAccordingTo(Comparator.comparing(CurrencyView::code));
  }

  @Test
  void codesWithoutAMinorUnitAreNoCurrencyToPriceIn() {
    assertThat(currencies())
        .extracting(CurrencyView::code)
        .doesNotContain("XXX", "XTS", "XAU", "XDR");
  }

  private List<CurrencyView> currencies() {
    return http.get()
        .uri("/currencies")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<List<CurrencyView>>() {})
        .returnResult()
        .getResponseBody();
  }
}
