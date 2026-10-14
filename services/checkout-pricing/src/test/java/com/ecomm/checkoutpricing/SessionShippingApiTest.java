package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.checkoutpricing.domain.ShippingAddress;
import com.ecomm.commons.money.Money;
import com.ecomm.commons.security.FakeKeycloak;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * A Checkout Session takes a Shipping address, validated by the mock {@code AddressValidator}, and
 * keeps the Shipping options Fulfillment & Shipping quotes for it; then one of those options, whose
 * Shipping cost it keeps for its lifetime and adds to its total, untouched by Discounts. It can't
 * be paid without both.
 */
class SessionShippingApiTest extends CheckoutApiTest {

  @Test
  void aValidAddressComesBackNormalisedWithTheOptionsQuotedForIt() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    setShippingAddress(
            sessionId,
            """
            {"recipientName": "  Erika  Mustermann ", "line1": "Hauptstraße 5", "line2": " ",
             "postcode": " 10115", "city": "Berlin", "country": "de"}
            """)
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"id": "%s",
             "shippingAddress": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5",
                                 "postcode": "10115", "city": "Berlin", "country": "DE"},
             "shippingOptions": [
               {"id": "STANDARD", "carrier": "Mock Carrier",
                "cost": {"amountMinor": 499, "currency": "EUR"}, "estimatedDays": 2},
               {"id": "EXPRESS", "carrier": "Mock Carrier",
                "cost": {"amountMinor": 999, "currency": "EUR"}, "estimatedDays": 1}],
             "shippingOption": null,
             "shippingCost": {"amountMinor": 0, "currency": "EUR"},
             "total": {"amountMinor": 159800, "currency": "EUR"}}
            """
                .formatted(sessionId));
  }

  @Test
  void theQuoteIsAskedForWithTheAddressTheLinesAndTheCurrencyAsCheckout() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    setShippingAddress(sessionId, BERLIN).expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(QUOTES_PATH))
            .withHeader("Authorization", equalTo("Bearer checkout-token-1"))
            .withRequestBody(
                equalToJson(
                    """
                    {"destination": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5",
                                     "postcode": "10115", "city": "Berlin", "country": "DE"},
                     "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 2}],
                     "currency": "EUR"}
                    """)));
  }

  @Test
  void anInvalidAddressNamesEachFieldAndChangesNothing() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    DOWNSTREAM.resetRequests();

    setShippingAddress(
            sessionId,
            """
            {"recipientName": "Erika Mustermann", "line1": "", "postcode": "1011",
             "city": "Berlin", "country": "DE"}
            """)
        .expectStatus()
        .isEqualTo(422)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("invalidShippingAddress")
        .jsonPath("$.errors[0].field")
        .isEqualTo("line1")
        .jsonPath("$.errors[1].field")
        .isEqualTo("postcode")
        .jsonPath("$.errors[1].message")
        .isEqualTo("isn't a postcode in DE, such as 10115")
        .jsonPath("$.errors.length()")
        .isEqualTo(2);

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo(QUOTES_PATH)));
    currentSession().expectStatus().isOk().expectBody().jsonPath("$.shippingAddress").isEmpty();
  }

  @Test
  void aFieldThatIsntTextIsNamed() {
    stubSuccessfulCheckout();

    setShippingAddress(
            startedSessionId(),
            """
            {"recipientName": "Erika Mustermann", "line1": 5, "postcode": "10115",
             "city": "Berlin", "country": "DE"}
            """)
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("line1")
        .jsonPath("$.errors[0].message")
        .isEqualTo("must be text");
  }

  @Test
  void aCountryTheStoreDoesntShipToIsUndeliverable() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    setShippingAddress(sessionId, "{\"country\": \"US\"}")
        .expectStatus()
        .isEqualTo(422)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("undeliverableCountry")
        .jsonPath("$.country")
        .isEqualTo("US");

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo(QUOTES_PATH)));
  }

  @Test
  void aCountryTheCarrierDoesntServeIsUndeliverableToo() {
    stubSuccessfulCheckout();
    stubQuote(
        aResponse()
            .withStatus(422)
            .withHeader("Content-Type", "application/problem+json")
            .withBody(
                """
                {"type": "about:blank", "title": "Unprocessable Content", "status": 422,
                 "reason": "shippingUnavailable"}
                """));

    setShippingAddress(startedSessionId(), BERLIN)
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("undeliverableCountry");
  }

  @Test
  void fulfillmentAndShippingBeingDownIsServiceUnavailableAndChangesNothing() {
    stubSuccessfulCheckout();
    var sessionId = shippedSessionId();
    stubQuote(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

    setShippingAddress(
            sessionId,
            """
            {"recipientName": "Jan Jansen", "line1": "Damrak 1", "postcode": "1012 LG",
             "city": "Amsterdam", "country": "NL"}
            """)
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("shippingUnavailable");

    currentSession()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.shippingAddress.city")
        .isEqualTo("Berlin")
        .jsonPath("$.shippingOption.id")
        .isEqualTo("EXPRESS");
  }

  @Test
  void aServerErrorFromFulfillmentAndShippingIsServiceUnavailableToo() {
    stubSuccessfulCheckout();
    stubQuote(aResponse().withStatus(500));

    setShippingAddress(startedSessionId(), BERLIN)
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("shippingUnavailable");
  }

  @Test
  void aQuotedOptionChosenIsKeptWithItsCostInTheTotal() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    setShippingAddress(sessionId, BERLIN).expectStatus().isOk();

    chooseShippingOption(sessionId, "EXPRESS")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"shippingOption": {"id": "EXPRESS", "carrier": "Mock Carrier",
                                "cost": {"amountMinor": 999, "currency": "EUR"},
                                "estimatedDays": 1},
             "shippingCost": {"amountMinor": 999, "currency": "EUR"},
             "subtotal": {"amountMinor": 159800, "currency": "EUR"},
             "total": {"amountMinor": 160799, "currency": "EUR"}}
            """);
  }

  @Test
  void onlyAQuotedOptionCanBeChosen() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    chooseShippingOption(sessionId, "EXPRESS")
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("shippingOptionNotQuoted");

    setShippingAddress(sessionId, BERLIN).expectStatus().isOk();
    chooseShippingOption(sessionId, "OVERNIGHT")
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("shippingOptionNotQuoted");
    currentSession().expectStatus().isOk().expectBody().jsonPath("$.shippingOption").isEmpty();
  }

  @Test
  void theCostQuotedIsKeptForTheSessionsLifetime() {
    stubSuccessfulCheckout();
    var sessionId = shippedSessionId();
    stubQuote(quotedOption("STANDARD", 599, 2), quotedOption("EXPRESS", 1299, 1));

    removeCoupon(sessionId).expectStatus().isOk();

    currentSession()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.shippingCost.amountMinor")
        .isEqualTo(999);
  }

  @Test
  void discountsNeverTouchTheShippingCost() {
    stubSuccessfulCheckout();
    stubDiscount("ALLFREE", 159800);
    var sessionId = shippedSessionId();

    applyCoupon(sessionId, "ALLFREE")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"shippingCost": {"amountMinor": 999, "currency": "EUR"},
             "total": {"amountMinor": 999, "currency": "EUR"}}
            """);
  }

  @Test
  void changingTheAddressRequotesAndClearsAnOptionNoLongerOffered() {
    stubSuccessfulCheckout();
    var sessionId = shippedSessionId();
    stubQuote(quotedOption("STANDARD", 999, 4));

    setShippingAddress(
            sessionId,
            """
            {"recipientName": "Marie Dupont", "line1": "1 Rue de Rivoli", "postcode": "75001",
             "city": "Paris", "country": "FR"}
            """)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.shippingOptions.length()")
        .isEqualTo(1)
        .jsonPath("$.shippingOption")
        .isEmpty()
        .jsonPath("$.shippingCost.amountMinor")
        .isEqualTo(0);
  }

  @Test
  void changingTheAddressKeepsAnOptionStillOfferedAtItsNewCost() {
    stubSuccessfulCheckout();
    var sessionId = shippedSessionId();
    stubQuote(quotedOption("STANDARD", 999, 4), quotedOption("EXPRESS", 1999, 2));

    setShippingAddress(
            sessionId,
            """
            {"recipientName": "Marie Dupont", "line1": "1 Rue de Rivoli", "postcode": "75001",
             "city": "Paris", "country": "FR"}
            """)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.shippingOption.id")
        .isEqualTo("EXPRESS")
        .jsonPath("$.shippingCost.amountMinor")
        .isEqualTo(1999);
  }

  @Test
  void payingWithoutAnAddressOrAnOptionIsAConflictAndStartsNothing() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    pay(sessionId)
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("shippingIncomplete");
    setShippingAddress(sessionId, BERLIN).expectStatus().isOk();
    pay(sessionId).expectStatus().isEqualTo(409);

    assertThat(saga.started()).isEmpty();
  }

  @Test
  void theSagaIsStartedWithTheShippingAddressAndTheChosenOption() {
    stubSuccessfulCheckout();

    checkout().expectStatus().isOk();

    var session = saga.started().getFirst().session();
    assertThat(session.shippingAddress())
        .isEqualTo(
            new ShippingAddress(
                "Erika Mustermann", "Hauptstraße 5", null, "10115", "Berlin", null, "DE", null));
    assertThat(session.shippingOption().id()).isEqualTo("EXPRESS");
    assertThat(session.shippingOption().cost()).isEqualTo(Money.of(999, "EUR"));
  }

  @Test
  void anotherCustomersSessionIsNotFound() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    setShippingAddress(FakeKeycloak.token("customer-7", "CUSTOMER"), sessionId, BERLIN)
        .expectStatus()
        .isNotFound();
  }

  @Test
  void anAddressThatIsntAnObjectIsABadRequest() {
    stubSuccessfulCheckout();

    setShippingAddress(startedSessionId(), "\"Berlin\"").expectStatus().isBadRequest();
  }
}
