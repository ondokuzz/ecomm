package com.ecomm.fulfillmentshipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checkout asks what shipping a destination costs: the Warehouse that will ship it, and the mock
 * carrier's Shipping options from there.
 */
class ShippingQuoteApiTest extends FulfillmentShippingApiTest {

  private static final String LINES =
      """
      [{"variantId": "PHN-PIXEL-9-BLK-128", "quantity": 1}, {"variantId": "AUD-1", "quantity": 2}]
      """;

  @Test
  void aGermanAddressShipsFromBerlinAtDomesticPrices() {
    quote(address("DE", "10115"), LINES, "EUR")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"warehouseId": "WH-BER",
             "options": [
               {"id": "STANDARD", "carrier": "Mock Carrier",
                "cost": {"amountMinor": 499, "currency": "EUR"}, "estimatedDays": 2},
               {"id": "EXPRESS", "carrier": "Mock Carrier",
                "cost": {"amountMinor": 999, "currency": "EUR"}, "estimatedDays": 1}]}
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void aDutchAddressShipsFromAmsterdamAtDomesticPrices() {
    quote(address("NL", "1012 AB"), LINES, "EUR")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.warehouseId")
        .isEqualTo("WH-AMS")
        .jsonPath("$.options[0].cost.amountMinor")
        .isEqualTo(499)
        .jsonPath("$.options[1].cost.amountMinor")
        .isEqualTo(999);
  }

  @Test
  void anAddressInACountryWithNoWarehouseShipsFromTheDefaultAtEuPrices() {
    quote(address("FR", "75001"), LINES, "EUR")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"warehouseId": "WH-BER",
             "options": [
               {"id": "STANDARD", "carrier": "Mock Carrier",
                "cost": {"amountMinor": 999, "currency": "EUR"}, "estimatedDays": 4},
               {"id": "EXPRESS", "carrier": "Mock Carrier",
                "cost": {"amountMinor": 1999, "currency": "EUR"}, "estimatedDays": 2}]}
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void aCountryTheCarrierDoesNotServeHasNoShipping() {
    expectShippingUnavailable(quote(address("US", "10001"), LINES, "EUR"));
  }

  @Test
  void aCurrencyTheCarrierDoesNotPriceInHasNoShipping() {
    expectShippingUnavailable(quote(address("DE", "10115"), LINES, "USD"));
  }

  @Test
  void aCustomerMayNotQuote() {
    quoteAs(CUSTOMER, address("DE", "10115"), LINES, "EUR").expectStatus().isForbidden();
  }

  @Test
  void staffMayNotQuote() {
    quoteAs(STAFF, address("DE", "10115"), LINES, "EUR").expectStatus().isForbidden();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "null | " + ONE_LINE + " | \"EUR\" | destination",
        "{\"postcode\": \"10115\"} | " + ONE_LINE + " | \"EUR\" | destination.country",
        "{\"country\": \"de\"} | " + ONE_LINE + " | \"EUR\" | destination.country",
        "{\"country\": \"XX\"} | " + ONE_LINE + " | \"EUR\" | destination.country",
        "{\"country\": \"DE\"} | [] | \"EUR\" | lines",
        "{\"country\": \"DE\"} | [{\"quantity\": 1}] | \"EUR\" | lines[0].variantId",
        "{\"country\": \"DE\"} | [{\"variantId\": \"A\", \"quantity\": 0}] | \"EUR\""
            + " | lines[0].quantity",
        "{\"country\": \"DE\"} | [{\"variantId\": \"A\", \"quantity\": 1.5}] | \"EUR\""
            + " | lines[0].quantity",
        "{\"country\": \"DE\"} | " + ONE_LINE + " | \"EURO\" | currency",
        "{\"country\": \"DE\"} | " + ONE_LINE + " | 978 | currency",
      })
  void aMalformedRequestIsABadRequestNamingTheField(
      String destination, String lines, String currency, String field) {
    http.post()
        .uri("/shipping/quotes")
        .headers(h -> h.setBearerAuth(CHECKOUT))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"destination": %s, "lines": %s, "currency": %s}
            """
                .formatted(destination, lines, currency))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo(field);
  }

  private static final String ONE_LINE = "[{\"variantId\": \"A\", \"quantity\": 1}]";

  private static void expectShippingUnavailable(RestTestClient.ResponseSpec response) {
    response
        .expectStatus()
        .isEqualTo(422)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("shippingUnavailable");
  }

  static String address(String country, String postcode) {
    return """
        {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5", "postcode": "%s",
         "city": "Somewhere", "country": "%s"}
        """
        .formatted(postcode, country);
  }

  RestTestClient.ResponseSpec quote(String destination, String lines, String currency) {
    return quoteAs(CHECKOUT, destination, lines, currency);
  }

  RestTestClient.ResponseSpec quoteAs(
      String token, String destination, String lines, String currency) {
    return http.post()
        .uri("/shipping/quotes")
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"destination": %s, "lines": %s, "currency": %s}
            """
                .formatted(destination, lines, currency == null ? "null" : "\"" + currency + "\""))
        .exchange();
  }
}
