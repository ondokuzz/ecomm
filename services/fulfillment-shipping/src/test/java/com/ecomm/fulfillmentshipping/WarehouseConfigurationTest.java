package com.ecomm.fulfillmentshipping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The service doesn't start on Warehouses that can't assign every destination exactly one: none or
 * two defaults, an ID twice, or two Warehouses in one country.
 */
class WarehouseConfigurationTest {

  private static final String BERLIN = "ecomm.fulfillment-shipping.warehouses[0].";
  private static final String AMSTERDAM = "ecomm.fulfillment-shipping.warehouses[1].";

  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(WarehouseConfiguration.class)
          .withPropertyValues(
              BERLIN + "id=WH-BER",
              BERLIN + "name=Berlin",
              BERLIN + "address.country=DE",
              AMSTERDAM + "id=WH-AMS",
              AMSTERDAM + "name=Amsterdam",
              AMSTERDAM + "address.country=NL");

  @Test
  void oneDefaultStarts() {
    context
        .withPropertyValues(BERLIN + "is-default=true")
        .run(started -> assertThat(started).hasNotFailed());
  }

  @Test
  void noDefaultDoesNotStart() {
    context.run(
        started ->
            assertThat(started)
                .getFailure()
                .rootCause()
                .hasMessageContaining("Exactly one Warehouse must be the default, but 0 are"));
  }

  @Test
  void twoDefaultsDoNotStart() {
    context
        .withPropertyValues(BERLIN + "is-default=true", AMSTERDAM + "is-default=true")
        .run(
            started ->
                assertThat(started)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("Exactly one Warehouse must be the default, but 2 are"));
  }

  @Test
  void anIdTwiceDoesNotStart() {
    context
        .withPropertyValues(BERLIN + "is-default=true", AMSTERDAM + "id=WH-BER")
        .run(
            started ->
                assertThat(started)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("Warehouse WH-BER is configured twice"));
  }

  @Test
  void twoWarehousesInOneCountryDoNotStart() {
    context
        .withPropertyValues(BERLIN + "is-default=true", AMSTERDAM + "address.country=DE")
        .run(
            started ->
                assertThat(started)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("Two Warehouses are in DE"));
  }

  @Test
  void aCountryThatIsNotAnIsoCodeDoesNotStart() {
    context
        .withPropertyValues(BERLIN + "is-default=true", AMSTERDAM + "address.country=Holland")
        .run(
            started ->
                assertThat(started)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("WH-AMS needs an ISO 3166-1 alpha-2 country"));
  }
}
