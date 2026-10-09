package com.ecomm.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Once an Order is paid, the checkout Saga clears the named Customer's Cart with Orchestration's
 * own token. Clearing an empty Cart changes nothing, so the command takes no key. Only
 * Orchestration may call it.
 */
class OrchestrationClearCartApiTest extends CartApiTest {

  private static final String ORCHESTRATION = FakeKeycloak.token("orchestration", "ORCHESTRATION");

  @Test
  void orchestrationClearsTheNamedCustomersCartAndNoOtherOne() {
    setQuantity("customer-erin", "PHN-PIXEL-9", 2).expectStatus().isOk();
    setQuantity("customer-frank", "AUD-JBL-FLIP-6", 1).expectStatus().isOk();

    clear(ORCHESTRATION, "{\"customerId\": \"customer-erin\"}").expectStatus().isNoContent();

    assertThat(itemsOf("customer-erin")).isEmpty();
    assertThat(itemsOf("customer-frank")).containsExactly(new CartItemView("AUD-JBL-FLIP-6", 1));
  }

  @Test
  void clearingAnEmptyCartAgainIsStillNoContent() {
    setQuantity("customer-gina", "PHN-PIXEL-9", 2).expectStatus().isOk();
    clear(ORCHESTRATION, "{\"customerId\": \"customer-gina\"}").expectStatus().isNoContent();

    clear(ORCHESTRATION, "{\"customerId\": \"customer-gina\"}").expectStatus().isNoContent();

    assertThat(itemsOf("customer-gina")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"customerId\": \" \"}", "{\"customerId\": 42}", "not json"})
  void aClearThatDoesNotNameACustomerIsABadRequest(String body) {
    clear(ORCHESTRATION, body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "CHECKOUT", "STAFF"})
  void onlyOrchestrationClearsANamedCustomersCart(String role) {
    setQuantity("customer-hal", "PHN-PIXEL-9", 2).expectStatus().isOk();

    clear(FakeKeycloak.token("customer-hal", role), "{\"customerId\": \"customer-hal\"}")
        .expectStatus()
        .isForbidden();

    assertThat(itemsOf("customer-hal")).containsExactly(new CartItemView("PHN-PIXEL-9", 2));
  }

  private RestTestClient.ResponseSpec clear(String token, String body) {
    return http.post()
        .uri("/carts/clear")
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }
}
