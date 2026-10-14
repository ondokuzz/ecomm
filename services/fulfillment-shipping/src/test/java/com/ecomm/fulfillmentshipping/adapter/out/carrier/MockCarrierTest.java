package com.ecomm.fulfillmentshipping.adapter.out.carrier;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.money.Money;
import com.ecomm.fulfillmentshipping.adapter.out.carrier.MockCarrier.Option;
import com.ecomm.fulfillmentshipping.adapter.out.carrier.MockCarrier.Rate;
import com.ecomm.fulfillmentshipping.adapter.out.carrier.MockCarrier.Zone;
import com.ecomm.fulfillmentshipping.domain.Line;
import com.ecomm.fulfillmentshipping.domain.ShippingOption;
import com.ecomm.fulfillmentshipping.domain.Warehouse;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The mock carrier's zone pricing: domestic in the origin Warehouse's country, EU elsewhere. */
class MockCarrierTest {

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final Currency SEK = Currency.getInstance("SEK");

  private static final Warehouse VIENNA =
      new Warehouse(
          "WH-VIE", "Vienna", new Warehouse.Address("Lager 1", "1010", "Wien", "AT"), true);

  private static final List<Line> LINES = List.of(new Line("V-1", 3));

  private final MockCarrier carrier =
      new MockCarrier(
          "Test Post",
          Set.of("AT", "SE"),
          Map.of(
              EUR,
              List.of(
                  new Option(
                      "SLOW", Map.of(Zone.DOMESTIC, new Rate(100, 5), Zone.EU, new Rate(300, 9))),
                  new Option(
                      "FAST", Map.of(Zone.DOMESTIC, new Rate(200, 1), Zone.EU, new Rate(600, 3)))),
              SEK,
              List.of(
                  new Option(
                      "SLOW",
                      Map.of(Zone.DOMESTIC, new Rate(1000, 5), Zone.EU, new Rate(3000, 9))))),
          null);

  @Test
  void aDestinationInTheOriginsCountryIsPricedDomestic() {
    assertThat(carrier.quote(VIENNA, "AT", LINES, EUR))
        .containsExactly(
            new ShippingOption("SLOW", "Test Post", Money.of(100, "EUR"), 5),
            new ShippingOption("FAST", "Test Post", Money.of(200, "EUR"), 1));
  }

  @Test
  void aDestinationElsewhereIsPricedEu() {
    assertThat(carrier.quote(VIENNA, "SE", LINES, EUR))
        .containsExactly(
            new ShippingOption("SLOW", "Test Post", Money.of(300, "EUR"), 9),
            new ShippingOption("FAST", "Test Post", Money.of(600, "EUR"), 3));
  }

  @Test
  void eachCurrencyHasItsOwnTable() {
    assertThat(carrier.quote(VIENNA, "SE", LINES, SEK))
        .containsExactly(new ShippingOption("SLOW", "Test Post", Money.of(3000, "SEK"), 9));
  }

  @Test
  void aCountryItDoesNotServeGetsNoOptions() {
    assertThat(carrier.quote(VIENNA, "DE", LINES, EUR)).isEmpty();
  }

  @Test
  void aCurrencyItDoesNotPriceInGetsNoOptions() {
    assertThat(carrier.quote(VIENNA, "AT", LINES, Currency.getInstance("USD"))).isEmpty();
  }
}
