package com.ecomm.fulfillmentshipping.adapter.out.carrier;

import com.ecomm.fulfillmentshipping.application.port.out.ShippingProvider;
import com.ecomm.fulfillmentshipping.application.port.out.TimeSource;
import java.time.Duration;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@link ShippingProvider}: the mock carrier, from {@code
 * ecomm.fulfillment-shipping.mock-carrier}. A Currency's options must each price both zones, or the
 * service doesn't start. It signs its Carrier webhooks with {@code
 * ecomm.fulfillment-shipping.carrier-webhooks.secret}, which has no default.
 */
@Configuration
@EnableConfigurationProperties({
  MockCarrierConfiguration.MockCarrierProperties.class,
  MockCarrierConfiguration.CarrierWebhookProperties.class
})
class MockCarrierConfiguration {

  /**
   * @param name the Carrier's name, as quotes show it
   * @param countries where it ships, as ISO 3166-1 alpha-2 codes
   * @param prices by ISO 4217 code, then by option ID in the order quotes list them, then by zone
   *     ({@code domestic}, {@code eu})
   * @param trackingDelays how long after booking each Tracking event is sent
   * @param webhookUrl where its Carrier webhooks go: the service's own endpoint unless set
   */
  @ConfigurationProperties("ecomm.fulfillment-shipping.mock-carrier")
  record MockCarrierProperties(
      String name,
      Set<String> countries,
      Map<String, LinkedHashMap<String, Map<MockCarrier.Zone, MockCarrier.Rate>>> prices,
      @DefaultValue TrackingDelays trackingDelays,
      String webhookUrl) {

    record TrackingDelays(
        @DefaultValue("10s") Duration inTransit,
        @DefaultValue("20s") Duration outForDelivery,
        @DefaultValue("30s") Duration delivered) {}
  }

  /** {@code secret}: what Carrier webhooks are signed with, shared with the Carrier. */
  @ConfigurationProperties("ecomm.fulfillment-shipping.carrier-webhooks")
  record CarrierWebhookProperties(String secret) {}

  @Bean
  CarrierWebhookDelivery httpCarrierWebhookDelivery(
      MockCarrierProperties properties, RestClient.Builder http, Environment environment) {
    return new HttpCarrierWebhookDelivery(http.build(), properties.webhookUrl(), environment);
  }

  @Bean
  MockCarrierWebhooks mockCarrierWebhooks(
      MockCarrierProperties properties,
      CarrierWebhookProperties webhooks,
      CarrierWebhookDelivery delivery,
      JsonMapper json) {
    var delays = properties.trackingDelays();
    return new MockCarrierWebhooks(
        delivery,
        delays.inTransit(),
        delays.outForDelivery(),
        delays.delivered(),
        webhooks.secret(),
        json);
  }

  @Bean
  ShippingProvider mockCarrier(
      MockCarrierProperties properties, MockCarrierWebhooks webhooks, TimeSource time) {
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
    return new MockCarrier(properties.name(), properties.countries(), prices, webhooks, time);
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
