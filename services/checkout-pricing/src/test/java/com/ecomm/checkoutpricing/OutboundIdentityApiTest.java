package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Cart hears from the Customer, with their own token. Inventory and Promotions hear from Checkout,
 * with its own token, and Inventory is told which Customer the call is about.
 */
class OutboundIdentityApiTest extends CheckoutApiTest {

  private String sessionIdStartedWith(String token) {
    return startSession(token)
        .expectStatus()
        .isCreated()
        .expectBody(SessionView.class)
        .returnResult()
        .getResponseBody()
        .id();
  }

  @Test
  void cartCallsCarryTheCustomersToken() {
    stubSuccessfulCheckout();
    var token = customerToken();

    sessionIdStartedWith(token);

    DOWNSTREAM.verify(
        getRequestedFor(urlEqualTo("/cart"))
            .withHeader("Authorization", equalTo("Bearer " + token)));
  }

  @Test
  void catalogReadsArePublic() {
    stubSuccessfulCheckout();

    startedSessionId();

    DOWNSTREAM.verify(
        getRequestedFor(urlEqualTo("/variants/PHN-PIXEL-9")).withHeader("Authorization", absent()));
  }

  @Test
  void internalCallsCarryCheckoutsTokenAndNameTheCustomer() {
    stubSuccessfulCheckout();
    var checkoutToken = equalTo("Bearer checkout-token-1");
    var namesTheCustomer = matchingJsonPath("$.customerId", equalTo(CUSTOMER_ID));

    startedSessionId();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/reservations"))
            .withHeader("Authorization", checkoutToken)
            .withRequestBody(namesTheCustomer));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(EVALUATE_PATH)).withHeader("Authorization", checkoutToken));
  }

  @Test
  void releasingAReplacedReservationCarriesCheckoutsTokenAndNamesTheCustomer() {
    stubSuccessfulCheckout();
    startedSessionId();

    startedSessionId();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/reservations/" + RESERVATION_ID + "/release"))
            .withHeader("Authorization", equalTo("Bearer checkout-token-1"))
            .withRequestBody(matchingJsonPath("$.customerId", equalTo(CUSTOMER_ID))));
  }

  @Test
  void checkoutsTokenIsFetchedWithItsClientCredentials() {
    stubSuccessfulCheckout();

    startedSessionId();

    var clientIdAndSecret = Base64.getEncoder().encodeToString("checkout:test".getBytes(UTF_8));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(TOKEN_PATH))
            .withHeader("Authorization", equalTo("Basic " + clientIdAndSecret))
            .withRequestBody(containing("grant_type=client_credentials")));
  }
}
