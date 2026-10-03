package com.ecomm.promotions;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;

import java.util.LinkedHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Campaign Staff send is checked field by field, and against Catalog's Categories and currencies.
 * Each 400 names the field at fault in its problem detail's {@code errors}.
 */
class CampaignValidationApiTest extends PromotionsApiTest {

  /** A valid Campaign with {@code field}'s JSON replaced by {@code json}, or left out for null. */
  private static String with(String field, String json) {
    var fields = new LinkedHashMap<String, String>();
    fields.put("name", "\"Invalid\"");
    fields.put("discount", "{\"type\": \"PERCENT_OFF\", \"percentOff\": 10}");
    fields.put("categories", "[\"audio\"]");
    fields.put("minimumSubtotal", "{\"amountMinor\": 1000, \"currency\": \"EUR\"}");
    fields.put("validFrom", "\"2020-01-01T00:00:00Z\"");
    fields.put("validUntil", "\"2100-01-01T00:00:00Z\"");
    fields.put("active", "true");
    fields.put("priority", "900");
    if (json == null) {
      fields.remove(field);
    } else {
      fields.put(field, json);
    }
    var body = new StringBuilder("{");
    fields.forEach(
        (k, v) ->
            body.append(body.length() > 1 ? ", " : "")
                .append('"')
                .append(k)
                .append("\": ")
                .append(v));
    return body.append('}').toString();
  }

  static Stream<Arguments> invalidCampaigns() {
    return Stream.of(
        // name
        Arguments.of("name", with("name", null)),
        Arguments.of("name", with("name", "\"  \"")),
        Arguments.of("name", with("name", "42")),
        Arguments.of("name", with("name", "\"" + "x".repeat(101) + "\"")),
        // discount
        Arguments.of("discount", with("discount", null)),
        Arguments.of("discount.type", with("discount", "{\"type\": \"HALF_OFF\"}")),
        Arguments.of("discount.percentOff", with("discount", "{\"type\": \"PERCENT_OFF\"}")),
        Arguments.of(
            "discount.percentOff",
            with("discount", "{\"type\": \"PERCENT_OFF\", \"percentOff\": 0}")),
        Arguments.of(
            "discount.percentOff",
            with("discount", "{\"type\": \"PERCENT_OFF\", \"percentOff\": 101}")),
        Arguments.of(
            "discount.percentOff",
            with("discount", "{\"type\": \"PERCENT_OFF\", \"percentOff\": 12.5}")),
        Arguments.of("discount.amountOff", with("discount", "{\"type\": \"AMOUNT_OFF\"}")),
        Arguments.of(
            "discount.amountOff",
            with(
                "discount",
                "{\"type\": \"AMOUNT_OFF\", \"amountOff\": {\"amountMinor\": 0, \"currency\": \"EUR\"}}")),
        Arguments.of(
            "discount.amountOff",
            with(
                "discount",
                "{\"type\": \"AMOUNT_OFF\", \"amountOff\": {\"amountMinor\": -5, \"currency\": \"EUR\"}}")),
        Arguments.of(
            "discount.amountOff.amountMinor",
            with(
                "discount",
                "{\"type\": \"AMOUNT_OFF\", \"amountOff\": {\"amountMinor\": 1.5, \"currency\": \"EUR\"}}")),
        Arguments.of(
            "discount.amountOff.currency",
            with(
                "discount",
                "{\"type\": \"AMOUNT_OFF\", \"amountOff\": {\"amountMinor\": 500, \"currency\": \"XYZ\"}}")),
        // A real currency, but not one Catalog lists.
        Arguments.of(
            "discount.amountOff.currency",
            with(
                "discount",
                "{\"type\": \"AMOUNT_OFF\", \"amountOff\": {\"amountMinor\": 500, \"currency\": \"GBP\"}}")),
        // categories
        Arguments.of("categories", with("categories", "\"audio\"")),
        Arguments.of("categories[1]", with("categories", "[\"audio\", 7]")),
        Arguments.of("categories[1]", with("categories", "[\"audio\", \"audio\"]")),
        Arguments.of("categories[1]", with("categories", "[\"phones\", \"toasters\"]")),
        // minimumSubtotal
        Arguments.of(
            "minimumSubtotal",
            with("minimumSubtotal", "{\"amountMinor\": -1, \"currency\": \"EUR\"}")),
        Arguments.of("minimumSubtotal.currency", with("minimumSubtotal", "{\"amountMinor\": 100}")),
        Arguments.of(
            "minimumSubtotal.currency",
            with("minimumSubtotal", "{\"amountMinor\": 100, \"currency\": \"CHF\"}")),
        // validity
        Arguments.of("validFrom", with("validFrom", null)),
        Arguments.of("validFrom", with("validFrom", "\"2020-01-01\"")),
        Arguments.of("validUntil", with("validUntil", null)),
        Arguments.of("validUntil", with("validUntil", "\"2020-01-01T00:00:00Z\"")),
        Arguments.of("validUntil", with("validUntil", "\"2019-01-01T00:00:00Z\"")),
        // active
        Arguments.of("active", with("active", null)),
        Arguments.of("active", with("active", "\"yes\"")),
        // priority
        Arguments.of("priority", with("priority", null)),
        Arguments.of("priority", with("priority", "1.5")),
        Arguments.of("priority", with("priority", "\"1\"")),
        Arguments.of("priority", with("priority", "-1")));
  }

  @ParameterizedTest(name = "{0}: {1}")
  @MethodSource("invalidCampaigns")
  void anInvalidCampaignIsABadRequestNamingItsField(String field, String body) {
    expectInvalid(createCampaign(body), field);
  }

  @Test
  void aPriorityAnotherCampaignHasIsABadRequestNamingIt() {
    createdCampaign(campaign("Holds 901", 901, 10));

    expectInvalid(createCampaign(campaign("Wants 901", 901, 20)), "priority");

    var other = createdCampaign(campaign("Holds 902", 902, 10));
    expectInvalid(updateCampaign(other, campaign("Holds 902", 901, 10)), "priority");
  }

  @Test
  void aReplacedCampaignIsCheckedToo() {
    var id = createdCampaign(campaign("Checked on update", 903, 10));

    expectInvalid(
        updateCampaign(id, with("categories", "[\"toasters\"]").replace("900", "903")),
        "categories[0]");
    readCampaign(id).expectBody().jsonPath("$.categories").isEmpty();
  }

  @Test
  void aCategoryOrCurrencyCatalogHasSinceDroppedStaysWhenTheCampaignIsReplaced() {
    var body =
        """
        {"name": "Old Category", "discount": {"type": "AMOUNT_OFF",
         "amountOff": {"amountMinor": 500, "currency": "USD"}}, "categories": ["phones"],
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z",
         "active": %s, "priority": 904}
        """;
    var id = createdCampaign(body.formatted("true"));
    CATALOG.stubFor(
        get("/categories")
            .willReturn(
                okJson(
                    """
                    [{"slug": "audio", "name": "Audio", "productCount": 2, "attributes": []}]
                    """)));
    CATALOG.stubFor(
        get("/currencies")
            .willReturn(
                okJson(
                    """
                    [{"code": "EUR", "minorDigits": 2}]
                    """)));

    // Switching it off mustn't need Catalog to still have what it already had.
    updateCampaign(id, body.formatted("false")).expectStatus().isOk();
  }

  @Test
  void whenCatalogCannotBeReachedTheCampaignIsNotSaved() {
    CATALOG.stubFor(get("/categories").willReturn(serverError()));

    createCampaign(campaign("Catalog down", 905, 10).replace("[]", "[\"audio\"]"))
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void malformedJsonIsABadRequest() {
    createCampaign("not json")
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  private static void expectInvalid(RestTestClient.ResponseSpec response, String field) {
    response
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo(field)
        .jsonPath("$.errors[0].message")
        .isNotEmpty();
  }
}
