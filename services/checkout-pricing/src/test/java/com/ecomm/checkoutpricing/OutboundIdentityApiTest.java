package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Cart hears from the Customer, with their own token. Inventory, Order Management and Payment hear
 * from Checkout, with its own token, and are told which Customer the call is about.
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

    pay(token, sessionIdStartedWith(token)).expectStatus().isOk();

    DOWNSTREAM.verify(
        getRequestedFor(urlEqualTo("/cart"))
            .withHeader("Authorization", equalTo("Bearer " + token)));
    DOWNSTREAM.verify(
        deleteRequestedFor(urlEqualTo("/cart"))
            .withHeader("Authorization", equalTo("Bearer " + token)));
  }

  @Test
  void catalogReadsArePublic() {
    stubSuccessfulCheckout();

    checkout().expectStatus().isOk();

    DOWNSTREAM.verify(
        getRequestedFor(urlEqualTo("/variants/PHN-PIXEL-9")).withHeader("Authorization", absent()));
  }

  @Test
  void internalCallsCarryCheckoutsTokenAndNameTheCustomer() {
    stubSuccessfulCheckout();
    var checkoutToken = equalTo("Bearer checkout-token-1");
    var namesTheCustomer = matchingJsonPath("$.customerId", equalTo(CUSTOMER_ID));

    checkout().expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/reservations"))
            .withHeader("Authorization", checkoutToken)
            .withRequestBody(namesTheCustomer));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/reservations/" + RESERVATION_ID + "/commit"))
            .withHeader("Authorization", checkoutToken)
            .withRequestBody(namesTheCustomer));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/orders"))
            .withHeader("Authorization", checkoutToken)
            .withRequestBody(namesTheCustomer));
    DOWNSTREAM.verify(
        patchRequestedFor(urlEqualTo("/orders/" + ORDER_ID + "/status"))
            .withHeader("Authorization", checkoutToken)
            .withRequestBody(namesTheCustomer)
            .withRequestBody(matchingJsonPath("$.status", equalTo("PAID"))));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/payments"))
            .withHeader("Authorization", checkoutToken)
            .withRequestBody(namesTheCustomer));
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

    checkout().expectStatus().isOk();

    var clientIdAndSecret = Base64.getEncoder().encodeToString("checkout:test".getBytes(UTF_8));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(TOKEN_PATH))
            .withHeader("Authorization", equalTo("Basic " + clientIdAndSecret))
            .withRequestBody(containing("grant_type=client_credentials")));
  }
}
