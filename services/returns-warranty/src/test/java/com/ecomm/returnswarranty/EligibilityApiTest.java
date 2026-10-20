package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A Customer asks what each line of their delivered Order is still eligible for: when its Return
 * Period and Warranty Window end, and which units are still free to claim.
 */
class EligibilityApiTest extends RmasApiTest {

  record LineView(
      String variantId,
      String productName,
      String returnPeriodEndsAt,
      String warrantyWindowEndsAt,
      List<String> serialNumbers,
      int quantity) {}

  record EligibilityView(String orderId, List<LineView> lines) {}

  private EligibilityView eligibility(String token, String orderId) {
    return http.get()
        .uri("/rmas/eligibility?orderId={orderId}", orderId)
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(EligibilityView.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  void eachDeliveredLineSaysItsWindowsAndWhatIsLeftToClaim() {
    var phone = newVariant("PHONE");
    var cable = newVariant("CABLE");
    Products.product("Galaxy S25", phone).tracked("IMEI").warranty(24, "Samsung").publish();
    var claimed = newImei();
    var free = newImei();
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(phone, "Galaxy S25", 2, "IMEI", claimed, free)
            .line(cable, "USB-C cable", 3, "NONE")
            .delivered(RECENTLY);
    requested(rma(shipment.orderId(), phone, 1, "WARRANTY_CLAIM", claimed));
    requested(rma(shipment.orderId(), cable, 1, "RETURN"));

    var eligibility = eligibility(CUSTOMER, shipment.orderId());

    assertThat(eligibility.orderId()).isEqualTo(shipment.orderId());
    assertThat(eligibility.lines())
        .containsExactlyInAnyOrder(
            new LineView(
                phone,
                "Galaxy S25",
                "2026-10-26T12:00:00Z",
                "2028-10-12T12:00:00Z",
                List.of(free),
                1),
            new LineView(cable, "USB-C cable", "2026-10-26T12:00:00Z", null, List.of(), 2));
  }

  @Test
  void aRejectedRmasUnitsAreFreeAgain() {
    var cable = newVariant("CABLE");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(cable, "USB-C cable", 1, "NONE").delivered(RECENTLY);
    var rma = requested(rma(shipment.orderId(), cable, 1, "RETURN"));
    assertThat(eligibility(CUSTOMER, shipment.orderId()).lines().getFirst().quantity()).isZero();

    reject(STAFF, rma.id(), "{\"reason\": \"No\"}").expectStatus().isOk();

    assertThat(eligibility(CUSTOMER, shipment.orderId()).lines().getFirst().quantity())
        .isEqualTo(1);
  }

  @Test
  void anotherCustomersOrUndeliveredOrderIsNotFound() {
    var cable = newVariant("CABLE");
    var theirs =
        Shipments.shipment("customer-43").line(cable, "USB-C cable", 1, "NONE").delivered(RECENTLY);
    awaitShipmentsProjected();
    await()
        .atMost(Duration.ofSeconds(20))
        .until(
            () ->
                http.get()
                    .uri("/rmas/eligibility?orderId={orderId}", theirs.orderId())
                    .headers(h -> h.setBearerAuth(OTHER_CUSTOMER))
                    .exchange()
                    .returnResult()
                    .getStatus()
                    .value(),
            status -> status == 200);

    for (var orderId : new String[] {theirs.orderId(), "no-such-order"}) {
      http.get()
          .uri("/rmas/eligibility?orderId={orderId}", orderId)
          .headers(h -> h.setBearerAuth(CUSTOMER))
          .exchange()
          .expectStatus()
          .isNotFound();
    }
    http.get()
        .uri("/rmas/eligibility?orderId={orderId}", theirs.orderId())
        .headers(h -> h.setBearerAuth(STAFF))
        .exchange()
        .expectStatus()
        .isForbidden();
  }
}
