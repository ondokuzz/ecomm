package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * An Order is placed with the Shipping address and the Shipping option it was checked out with,
 * whose cost its total includes, and its events carry them. Orders placed before Shipping read
 * without either.
 */
class OrderShippingApiTest extends OrderApiTest {

  private static final String LINES =
      """
      "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 1,
                 "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}],
      "tax": {"amountMinor": 0, "currency": "EUR"}
      """;

  @Test
  void anOrderRecordsItsShippingAndItsTotalIncludesTheShippingCost() {
    place(twoLineOrder(CUSTOMER))
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            """
            {"shippingAddress": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5",
                                 "line2": "2. OG", "postcode": "10115", "city": "Berlin",
                                 "country": "DE", "phone": "+49 30 1234567"},
             "shippingOption": {"id": "EXPRESS", "carrier": "Mock Carrier",
                                "cost": {"amountMinor": 999, "currency": "EUR"}},
             "subtotal": {"amountMinor": 174750, "currency": "EUR"},
             "total": {"amountMinor": 175749, "currency": "EUR"}}
            """);
  }

  @Test
  void theCustomerReadsTheirOrderWithItsShipping() {
    var id = placed().id();

    http.get()
        .uri("/orders/{id}", id)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.shippingAddress.city")
        .isEqualTo("Berlin")
        .jsonPath("$.shippingAddress.region")
        .doesNotExist()
        .jsonPath("$.shippingOption.id")
        .isEqualTo("EXPRESS")
        .jsonPath("$.total.amountMinor")
        .isEqualTo(175749);
  }

  @Test
  void discountsNeverTouchTheShippingCost() {
    // 799.00 of lines, a Discount of 799.05: more than the lines, though not the lines plus
    // Shipping.
    place(
            """
            {"customerId": "customer-42", %s,
             "discounts": [{"source": "COUPON", "couponCode": "BIG",
                            "amount": {"amountMinor": 79905, "currency": "EUR"}}],
             %s}
            """
                .formatted(LINES, SHIPPING))
        .expectStatus()
        .isBadRequest();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // No Shipping at all, as a checkout Saga run from before Sprint 5 places it.
        "",
        // An address without an option.
        """
        , "shippingAddress": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5",
                              "postcode": "10115", "city": "Berlin", "country": "DE"}
        """,
        // An option without an address.
        """
        , "shippingOption": {"id": "EXPRESS", "carrier": "Mock Carrier",
                             "cost": {"amountMinor": 999, "currency": "EUR"}}
        """,
        // An address without its required fields, one by one.
        """
        , "shippingAddress": {"line1": "Hauptstraße 5", "postcode": "10115", "city": "Berlin",
                              "country": "DE"}, "shippingOption": $OPTION
        """,
        """
        , "shippingAddress": {"recipientName": "Erika Mustermann", "postcode": "10115",
                              "city": "Berlin", "country": "DE"}, "shippingOption": $OPTION
        """,
        """
        , "shippingAddress": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5",
                              "city": "Berlin", "country": "DE"}, "shippingOption": $OPTION
        """,
        """
        , "shippingAddress": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5",
                              "postcode": "10115", "country": "DE"}, "shippingOption": $OPTION
        """,
        """
        , "shippingAddress": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5",
                              "postcode": "10115", "city": "Berlin"}, "shippingOption": $OPTION
        """,
        """
        , "shippingAddress": {"recipientName": " ", "line1": "Hauptstraße 5",
                              "postcode": "10115", "city": "Berlin", "country": "DE"},
          "shippingOption": $OPTION
        """,
        """
        , "shippingAddress": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5",
                              "postcode": "10115", "city": "Berlin", "country": "Germany"},
          "shippingOption": $OPTION
        """,
        """
        , "shippingAddress": {"recipientName": "Erika Mustermann", "line1": 5,
                              "postcode": "10115", "city": "Berlin", "country": "DE"},
          "shippingOption": $OPTION
        """,
        // An option without its ID, its carrier or its cost, or with a cost that can't be.
        """
        , "shippingAddress": $ADDRESS,
          "shippingOption": {"carrier": "Mock Carrier", "cost": {"amountMinor": 999, "currency": "EUR"}}
        """,
        """
        , "shippingAddress": $ADDRESS,
          "shippingOption": {"id": "EXPRESS", "cost": {"amountMinor": 999, "currency": "EUR"}}
        """,
        """
        , "shippingAddress": $ADDRESS, "shippingOption": {"id": "EXPRESS", "carrier": "Mock Carrier"}
        """,
        """
        , "shippingAddress": $ADDRESS,
          "shippingOption": {"id": "EXPRESS", "carrier": "Mock Carrier",
                             "cost": {"amountMinor": -1, "currency": "EUR"}}
        """,
        """
        , "shippingAddress": $ADDRESS,
          "shippingOption": {"id": "EXPRESS", "carrier": "Mock Carrier",
                             "cost": {"amountMinor": 999, "currency": "USD"}}
        """
      })
  void anOrderWithoutValidShippingIsRefused(String shipping) {
    var filled = shipping.replace("$ADDRESS", ADDRESS).replace("$OPTION", OPTION);

    place("{\"customerId\": \"customer-42\", " + LINES + filled + "}")
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void anOrderPlacedBeforeShippingReadsWithout() {
    // A Sprint 2 Order (db/testdata), placed before Orders had Shipping.
    http.get()
        .uri("/orders/{id}", "5e000000-0000-4000-8000-000000000001")
        .headers(h -> h.setBearerAuth(tokenOf("customer-sprint2")))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.shippingAddress")
        .doesNotExist()
        .jsonPath("$.shippingOption")
        .doesNotExist()
        .jsonPath("$.total.amountMinor")
        .isEqualTo(79900);
  }

  @Test
  void theOrdersEventsCarryItsShipping() {
    var id = placed("customer-events").id();
    changeStatus(orchestrationToken(), id, statusChange("customer-events", "PAID"))
        .expectStatus()
        .isOk();

    var records = OrderEvents.keyed(id, 2);

    assertThat(records).hasSize(2);
    assertThat(records).allSatisfy(r -> assertThat(OrderEvents.schemaViolationsOf(r)).isEmpty());
    var paid = OrderEvents.valueOf(records.getLast()).get("order");
    assertThat(paid.at("/shippingAddress/recipientName").asText()).isEqualTo("Erika Mustermann");
    assertThat(paid.at("/shippingAddress/line2").asText()).isEqualTo("2. OG");
    assertThat(paid.at("/shippingAddress/region").isMissingNode()).isTrue();
    assertThat(paid.at("/shippingAddress/country").asText()).isEqualTo("DE");
    assertThat(paid.at("/shippingOption/id").asText()).isEqualTo("EXPRESS");
    assertThat(paid.at("/shippingOption/carrier").asText()).isEqualTo("Mock Carrier");
    assertThat(paid.at("/shippingOption/cost/amountMinor").asLong()).isEqualTo(999);
    assertThat(paid.at("/total/amountMinor").asLong()).isEqualTo(175749);
  }
}
