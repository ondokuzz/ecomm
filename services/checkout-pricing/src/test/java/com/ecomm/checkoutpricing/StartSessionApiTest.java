package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Starting checkout prices the Cart from Catalog and holds its Stock for 15 minutes in a Checkout
 * Session, through a Reservation that outlives the session by 2 minutes. A Cart that can't be held
 * is refused, and a Customer has at most one session.
 */
class StartSessionApiTest extends CheckoutApiTest {

  @Test
  void startingASessionPricesTheCartAndHoldsItFor15Minutes() {
    stubSuccessfulCheckout();
    var before = Instant.now();

    startSession()
        .expectStatus()
        .isCreated()
        .expectHeader()
        .valueMatches("Location", "/checkout/sessions/[0-9a-f-]{36}")
        .expectBody()
        .jsonPath("$.lines[0].variantId")
        .isEqualTo("PHN-PIXEL-9")
        .jsonPath("$.lines[0].quantity")
        .isEqualTo(2)
        .jsonPath("$.lines[0].unitPrice.amountMinor")
        .isEqualTo(79900)
        .jsonPath("$.lines[0].lineTotal.amountMinor")
        .isEqualTo(159800)
        .jsonPath("$.subtotal.amountMinor")
        .isEqualTo(159800)
        .jsonPath("$.tax.amountMinor")
        .isEqualTo(0)
        .jsonPath("$.total.amountMinor")
        .isEqualTo(159800)
        .jsonPath("$.total.currency")
        .isEqualTo("EUR")
        .jsonPath("$.expiresAt")
        .value(
            String.class,
            expiresAt ->
                assertThat(Instant.parse(expiresAt))
                    .isBetween(
                        before.plus(Duration.ofMinutes(15)),
                        Instant.now().plus(Duration.ofMinutes(15))));
  }

  @Test
  void theReservationHoldsTheCartUntil2MinutesAfterTheSessionExpires() {
    stubSuccessfulCheckout();

    var session = currentOf(startedSessionId());

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/reservations"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42",
                     "items": [{"variantId": "PHN-PIXEL-9", "quantity": 2}]}
                    """,
                    true,
                    true))
            .withRequestBody(
                matchingJsonPath(
                    "$.expiresAt",
                    equalTo(session.expiresAt().plus(Duration.ofMinutes(2)).toString()))));
  }

  @Test
  void anEmptyCartIsABadRequest() {
    stubSuccessfulCheckout();
    stubCart("");

    startSession()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo("/reservations")));
  }

  @Test
  void aVariantMissingFromTheCatalogIsAConflictNamingIt() {
    stubSuccessfulCheckout();
    stubCart(
        """
        {"variantId": "PHN-PIXEL-9", "quantity": 1}, {"variantId": "PHN-GONE", "quantity": 1}
        """);
    DOWNSTREAM.stubFor(get("/variants/PHN-GONE").willReturn(notFound()));

    startSession()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.unknownVariants[0]")
        .isEqualTo("PHN-GONE")
        .jsonPath("$.unknownVariants.length()")
        .isEqualTo(1);

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo("/reservations")));
    currentSession().expectStatus().isNotFound();
  }

  @Test
  void anOutOfStockVariantIsAConflictNamingIt() {
    stubSuccessfulCheckout();
    stubReserve(
        aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/problem+json")
            .withBody(
                """
                {"status": 409, "detail": "Not enough stock", "insufficientStock": ["PHN-PIXEL-9"]}
                """));

    startSession()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.outOfStock[0]")
        .isEqualTo("PHN-PIXEL-9");

    currentSession().expectStatus().isNotFound();
  }

  @Test
  void aVariantInventoryDoesntStockIsOutOfStock() {
    stubSuccessfulCheckout();
    stubReserve(
        aResponse()
            .withStatus(404)
            .withHeader("Content-Type", "application/problem+json")
            .withBody(
                """
                {"status": 404, "detail": "Unknown", "unknownVariants": ["PHN-PIXEL-9"]}
                """));

    startSession()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.outOfStock[0]")
        .isEqualTo("PHN-PIXEL-9");
  }

  @Test
  void aCartPricedInTwoCurrenciesIsAConflict() {
    stubSuccessfulCheckout();
    stubCart(
        """
        {"variantId": "PHN-PIXEL-9", "quantity": 1}, {"variantId": "AUD-US", "quantity": 1}
        """);
    DOWNSTREAM.stubFor(
        get("/variants/AUD-US")
            .willReturn(
                okJson(
                    """
                    {"id": "AUD-US", "price": {"amountMinor": 100, "currency": "USD"},
                     "product": {"sku": "AUD-US", "category": "audio"}}
                    """)));

    startSession()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo("/reservations")));
  }

  @Test
  void withoutASessionThereIsNoCurrentOne() {
    currentSession()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void anExpiredSessionIsNoLongerCurrent() {
    stubSuccessfulCheckout();
    startedSessionId();

    clock.advance(Duration.ofMinutes(15));

    currentSession().expectStatus().isNotFound();
  }

  @Test
  void startingAgainReplacesTheSessionAndReleasesItsReservation() {
    stubSuccessfulCheckout();
    var first = startedSessionId();
    stubReserve("5e2d1c0b-0000-4000-8000-000000000002");

    var second = startedSessionId();

    assertThat(second).isNotEqualTo(first);
    currentOf(second);
    DOWNSTREAM.verify(
        1,
        postRequestedFor(urlEqualTo("/reservations/" + RESERVATION_ID + "/release"))
            .withRequestBody(equalToJson("{\"customerId\": \"customer-42\"}")));
    DOWNSTREAM.verify(0, postRequestedFor(urlPathMatching("/reservations/.*/commit")));
    pay(first).expectStatus().isNotFound();
  }

  @Test
  void theOldReservationIsReleasedBeforeTheNewOneIsMade() {
    stubSuccessfulCheckout();
    startedSessionId();

    startedSessionId();

    assertThat(inventoryCalls())
        .containsExactly(
            "POST /reservations",
            "POST /reservations/" + RESERVATION_ID + "/release",
            "POST /reservations");
  }

  @Test
  void anotherCustomersSessionIsNotReplaced() {
    stubSuccessfulCheckout();
    var theirs = startedSessionId();

    startSession(FakeKeycloak.token("customer-7", "CUSTOMER")).expectStatus().isCreated();

    currentOf(theirs);
    DOWNSTREAM.verify(0, postRequestedFor(urlPathMatching("/reservations/.*/release")));
  }

  /** Every call to Inventory's Reservations, in the order they were made. */
  private static List<String> inventoryCalls() {
    return DOWNSTREAM.getAllServeEvents().stream()
        .map(ServeEvent::getRequest)
        .sorted(Comparator.comparing(LoggedRequest::getLoggedDate))
        .filter(r -> r.getUrl().startsWith("/reservations"))
        .map(r -> r.getMethod() + " " + r.getUrl())
        .toList();
  }

  /** The Customer's current Checkout Session, which must be {@code sessionId}. */
  private SessionView currentOf(String sessionId) {
    var session =
        currentSession()
            .expectStatus()
            .isOk()
            .expectBody(SessionView.class)
            .returnResult()
            .getResponseBody();
    assertThat(session.id()).isEqualTo(sessionId);
    return session;
  }
}
