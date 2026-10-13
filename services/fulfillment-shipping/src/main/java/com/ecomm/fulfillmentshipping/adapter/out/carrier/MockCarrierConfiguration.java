package com.ecomm.fulfillmentshipping.adapter.out.carrier;

import com.ecomm.fulfillmentshipping.application.port.out.ShippingProvider;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@link ShippingProvider}: the mock carrier, from {@code
 * ecomm.fulfillment-shipping.mock-carrier}. A Currency's options must each price both zones, or the
 * service doesn't start.
 */
@Configuration
@EnableConfigurationProperties(MockCarrierConfiguration.MockCarrierProperties.class)
class MockCarrierConfiguration {

  /**
   * @param name the Carrier's name, as quotes show it
   * @param countries where it ships, as ISO 3166-1 alpha-2 codes
   * @param prices by ISO 4217 code, then by option ID in the order quotes list them, then by zone
   *     ({@code domestic}, {@code eu})
   */
  @ConfigurationProperties("ecomm.fulfillment-shipping.mock-carrier")
  record MockCarrierProperties(
      String name,
      Set<String> countries,
      Map<String, LinkedHashMap<String, Map<MockCarrier.Zone, MockCarrier.Rate>>> prices) {}

  @Bean
  ShippingProvider mockCarrier(MockCarrierProperties properties) {
    var prices = new LinkedHashMap<Currency, List<MockCarrier.Option>>();
    properties
        .prices()
        .forEach(
            (code, options) ->
                prices.put(
                    Currency.getInstance(code),
                    options.entrySet().stream()
                        .map(option -> option(code, option.getKey(), option.getValue()))
                        .toList()));
    return new MockCarrier(properties.name(), properties.countries(), prices);
  }

  private static MockCarrier.Option option(
      String currency, String id, Map<MockCarrier.Zone, MockCarrier.Rate> rates) {
    for (var zone : MockCarrier.Zone.values()) {
      if (rates.get(zone) == null) {
        throw new IllegalArgumentException(
            "The mock carrier's " + id + " in " + currency + " has no " + zone + " price");
      }
    }
    return new MockCarrier.Option(id, rates);
  }
}
