package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

/** A Customer checks out their Cart and gets a paid Order back. */
class CheckoutHappyPathApiTest extends CheckoutApiTest {

  @Test
  void checkingOutReturnsThePaidOrder() {
    stubSuccessfulCheckout();

    checkout()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.orderId")
        .isEqualTo(ORDER_ID)
        .jsonPath("$.status")
        .isEqualTo("PAID");
  }

  @Test
  void theStepsRunInOrder() {
    stubSuccessfulCheckout();

    checkout().expectStatus().isOk();

    var steps =
        DOWNSTREAM.getAllServeEvents().stream()
            .sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
            .map(ServeEvent::getRequest)
            .filter(r -> !r.getUrl().equals(TOKEN_PATH))
            .map(r -> r.getMethod() + " " + r.getUrl())
            .toList();
    assertThat(steps)
        .containsExactly(
            "GET /cart",
            "GET /variants/PHN-PIXEL-9",
            "POST /orders",
            "POST /stock/decrement",
            "POST /payments",
            "PATCH /orders/" + ORDER_ID + "/status",
            "DELETE /cart");
  }

  @Test
  void theCartIsClearedAfterwards() {
    stubSuccessfulCheckout();

    checkout().expectStatus().isOk();

    DOWNSTREAM.verify(1, deleteRequestedFor(urlEqualTo("/cart")));
  }
}
