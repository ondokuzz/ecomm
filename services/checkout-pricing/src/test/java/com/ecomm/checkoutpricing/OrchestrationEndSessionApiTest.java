package com.ecomm.checkoutpricing;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Once an Order is paid, the checkout Saga ends the named Customer's Checkout Session with
 * Orchestration's own token. It ends the session only while the Customer's pointer still names it,
 * and answers 204 either way, so a repeat is harmless. Only Orchestration may call it.
 */
class OrchestrationEndSessionApiTest extends CheckoutApiTest {

  private static final String ORCHESTRATION = FakeKeycloak.token("orchestration", "ORCHESTRATION");

  @BeforeEach
  void stubs() {
    stubSuccessfulCheckout();
  }

  @Test
  void orchestrationEndsTheCustomersCurrentSession() {
    var id = startedSessionId();

    end(ORCHESTRATION, id, CUSTOMER_ID).expectStatus().isNoContent();

    currentSession().expectStatus().isNotFound();
  }

  @Test
  void endingItAgainIsStillNoContent() {
    var id = startedSessionId();
    end(ORCHESTRATION, id, CUSTOMER_ID).expectStatus().isNoContent();

    end(ORCHESTRATION, id, CUSTOMER_ID).expectStatus().isNoContent();

    currentSession().expectStatus().isNotFound();
  }

  @Test
  void aSessionTheCustomerHasSinceReplacedLeavesTheNewOneCurrent() {
    var first = startedSessionId();
    var second = startedSessionId();

    end(ORCHESTRATION, first, CUSTOMER_ID).expectStatus().isNoContent();

    currentSession().expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(second);
  }

  @Test
  void namingAnotherCustomerEndsNothing() {
    var id = startedSessionId();

    end(ORCHESTRATION, id, "customer-43").expectStatus().isNoContent();

    currentSession().expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(id);
  }

  @Test
  void anUnknownSessionIsNoContent() {
    end(ORCHESTRATION, "no-such-session", CUSTOMER_ID).expectStatus().isNoContent();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"customerId\": \" \"}", "{\"customerId\": 42}", "not json"})
  void anEndThatDoesNotNameACustomerIsABadRequest(String body) {
    endWith(ORCHESTRATION, "s-1", body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "CHECKOUT", "STAFF"})
  void onlyOrchestrationEndsANamedCustomersSession(String role) {
    var id = startedSessionId();

    end(FakeKeycloak.token(CUSTOMER_ID, role), id, CUSTOMER_ID).expectStatus().isForbidden();

    currentSession().expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(id);
  }

  private RestTestClient.ResponseSpec end(String token, String sessionId, String customerId) {
    return endWith(token, sessionId, "{\"customerId\": \"%s\"}".formatted(customerId));
  }

  private RestTestClient.ResponseSpec endWith(String token, String sessionId, String body) {
    return http.post()
        .uri("/checkout/sessions/{id}/end", sessionId)
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }
}
