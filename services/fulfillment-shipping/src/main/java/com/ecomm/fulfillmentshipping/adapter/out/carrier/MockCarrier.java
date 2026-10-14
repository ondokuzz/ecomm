package com.ecomm.fulfillmentshipping.adapter.out.carrier;

import com.ecomm.commons.money.Money;
import com.ecomm.fulfillmentshipping.application.port.out.ShippingProvider;
import com.ecomm.fulfillmentshipping.domain.Booking;
import com.ecomm.fulfillmentshipping.domain.Line;
import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShippingOption;
import com.ecomm.fulfillmentshipping.domain.Warehouse;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Carrier that stands in for a real one (ADR 0005). It ships to a set of countries and prices
 * each option from a table per Currency, by zone: {@link Zone#DOMESTIC} when the destination is in
 * the origin Warehouse's country, {@link Zone#EU} otherwise. The lines don't change the price.
 *
 * <p>It books every Shipment, with a fresh {@code MC} tracking number, and then reports its
 * Tracking events through {@link MockCarrierWebhooks}. As a real Carrier does, it answers a
 * repeated idempotency key with its first booking, for the most recent {@value #REMEMBERED_KEYS}
 * keys, until the service restarts.
 */
class MockCarrier implements ShippingProvider {

  enum Zone {
    DOMESTIC,
    EU
  }

  /** What an option costs, and how many days it takes, in one zone. */
  record Rate(long costMinor, int days) {}

  /** One option's rates, by zone. */
  record Option(String id, Map<Zone, Rate> rates) {}

  private static final int REMEMBERED_KEYS = 10_000;

  private final String name;
  private final Set<String> countries;
  private final Map<Currency, List<Option>> prices;
  private final MockCarrierWebhooks webhooks;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Booking> bookings =
      Collections.synchronizedMap(
          new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Booking> eldest) {
              return size() > REMEMBERED_KEYS;
            }
          });

  /**
   * @param countries the ISO 3166-1 alpha-2 codes of the countries it ships to
   * @param prices each Currency's options, in the order a quote lists them
   */
  MockCarrier(
      String name,
      Set<String> countries,
      Map<Currency, List<Option>> prices,
      MockCarrierWebhooks webhooks) {
    this.name = name;
    this.countries = Set.copyOf(countries);
    this.prices = Map.copyOf(prices);
    this.webhooks = webhooks;
  }

  @Override
  public Booking book(Shipment shipment, String idempotencyKey) {
    var booked = new Booking(name, "MC" + String.format("%010d", random.nextLong(10_000_000_000L)));
    var first = bookings.putIfAbsent(idempotencyKey, booked);
    if (first != null) {
      return first;
    }
    webhooks.scheduleFor(booked.trackingNumber(), Instant.now().truncatedTo(ChronoUnit.MILLIS));
    return booked;
  }

  @Override
  public List<ShippingOption> quote(
      Warehouse origin, String destinationCountry, List<Line> lines, Currency currency) {
    if (!countries.contains(destinationCountry)) {
      return List.of();
    }
    var zone = destinationCountry.equals(origin.country()) ? Zone.DOMESTIC : Zone.EU;
    return prices.getOrDefault(currency, List.of()).stream()
        .map(
            option -> {
              var rate = option.rates().get(zone);
              return new ShippingOption(
                  option.id(), name, new Money(rate.costMinor(), currency), rate.days());
            })
        .toList();
  }
}
