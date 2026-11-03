package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A Customer finds their RMA by the RMA reference they were told, such as {@code RMA-3F2A9C1B}, or
 * by its Order, as a page of their RMAs that match, and never finds anyone else's.
 */
class FindRmaByReferenceApiTest extends RmasApiTest {

  @Test
  void aCustomerFindsTheirRmaByItsReference() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 2, "NONE").delivered(RECENTLY);
    var rma = requested(rma(shipment.orderId(), charger, 1, "RETURN"));
    requested(rma(shipment.orderId(), charger, 1, "RETURN"));

    var page = find(CUSTOMER, rma.reference());

    assertThat(page.items()).containsExactly(rma);
    assertThat(page.total()).isEqualTo(1);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RMA-%s", "%s", "rma-%s", "Rma-%s"})
  void theReferenceIsFoundWithOrWithoutItsPrefixInEitherCase(String form) {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 1, "NONE").delivered(RECENTLY);
    var rma = requested(rma(shipment.orderId(), charger, 1, "RETURN"));
    var hex = rma.reference().substring("RMA-".length());

    assertThat(find(CUSTOMER, form.formatted(hex)).items()).containsExactly(rma);
    assertThat(find(CUSTOMER, form.formatted(hex.toLowerCase(Locale.ROOT))).items())
        .containsExactly(rma);
  }

  @Test
  void aCustomerFindsTheirRmasByTheirOrder() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 2, "NONE").delivered(RECENTLY);
    var other =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 1, "NONE").delivered(RECENTLY);
    var first = requested(rma(shipment.orderId(), charger, 1, "RETURN"));
    var second = requested(rma(shipment.orderId(), charger, 1, "RETURN"));
    requested(rma(other.orderId(), charger, 1, "RETURN"));

    var page = list(CUSTOMER, "?orderId=" + shipment.orderId());

    assertThat(page.items())
        .extracting(RmaView::id)
        .containsExactlyInAnyOrder(first.id(), second.id());
    assertThat(page.total()).isEqualTo(2);
  }

  @Test
  void anotherCustomersRmaIsNeverFound() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 1, "NONE").delivered(RECENTLY);
    var theirs = requested(rma(shipment.orderId(), charger, 1, "RETURN"));

    assertThat(find(OTHER_CUSTOMER, theirs.reference()).total()).isZero();
    assertThat(list(OTHER_CUSTOMER, "?orderId=" + shipment.orderId()).total()).isZero();
  }

  @Test
  void aReferenceNoRmaHasIsAnEmptyPage() {
    var page = find(CUSTOMER, "RMA-00000000");

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "RMA-", "3F2A9C1", "3F2A9C1B0", "RMA-3F2A9C1G", "#3F2A9C1B", "RMA3F2A9C1B"})
  void anythingButEightHexDigitsIsAnInvalidReference(String reference) {
    http.get()
        .uri(b -> b.path("/rmas").queryParam("reference", "{reference}").build(reference))
        .headers(h -> h.setBearerAuth(CUSTOMER))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("invalidReference")
        .jsonPath("$.errors[0].field")
        .isEqualTo("reference");
  }

  private PageView list(String token, String query) {
    return http.get()
        .uri("/rmas" + query)
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(PageView.class)
        .returnResult()
        .getResponseBody();
  }

  private PageView find(String token, String reference) {
    return http.get()
        .uri(b -> b.path("/rmas").queryParam("reference", "{reference}").build(reference))
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(PageView.class)
        .returnResult()
        .getResponseBody();
  }
}
