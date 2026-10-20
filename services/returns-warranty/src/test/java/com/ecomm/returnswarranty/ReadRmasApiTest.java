package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A Customer reads their own RMAs and nobody else's; Staff read any, and list them by status,
 * oldest first, a page at a time.
 */
class ReadRmasApiTest extends RmasApiTest {

  @Autowired TestTimeSource clock;

  @AfterEach
  void resetClock() {
    clock.reset();
  }

  PageView list(String token, String query) {
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

  @Test
  void aCustomerReadsTheirOwnRmaAndStaffReadAny() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 1, "NONE").delivered(RECENTLY);
    var rma = requested(rma(shipment.orderId(), charger, 1, "RETURN"));

    assertThat(readRma(CUSTOMER, rma.id())).isEqualTo(rma);
    assertThat(readRma(STAFF, rma.id())).isEqualTo(rma);
    read(OTHER_CUSTOMER, rma.id()).expectStatus().isNotFound();
    read(ORCHESTRATION, rma.id()).expectStatus().isForbidden();
    read(CUSTOMER, "not-an-id").expectStatus().isNotFound();
  }

  @Test
  void aCustomerListsOnlyTheirOwnRmas() {
    var charger = newVariant("CHARGER");
    var theirs =
        Shipments.shipment("customer-43").line(charger, "Charger", 1, "NONE").delivered(RECENTLY);
    awaitShipmentsProjected();
    request(OTHER_CUSTOMER, rma(theirs.orderId(), charger, 1, "RETURN")).expectStatus().isCreated();

    assertThat(list(CUSTOMER, "?orderId=" + theirs.orderId()).items()).isEmpty();
    var listed = list(OTHER_CUSTOMER, "?orderId=" + theirs.orderId());
    assertThat(listed.items()).extracting(RmaView::customerId).containsExactly("customer-43");
    assertThat(list(OTHER_CUSTOMER, "").items())
        .extracting(RmaView::customerId)
        .containsOnly("customer-43");
  }

  @Test
  void staffListAnOrdersRmasByStatusOldestFirstAPageAtATime() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 4, "NONE").delivered(RECENTLY);
    awaitShipmentsProjected();
    var second = requestedAt(Duration.ofHours(2), shipment.orderId(), charger);
    var first = requestedAt(Duration.ofHours(1), shipment.orderId(), charger);
    var third = requestedAt(Duration.ofHours(3), shipment.orderId(), charger);
    var fourth = requestedAt(Duration.ofHours(4), shipment.orderId(), charger);
    approve(STAFF, fourth.id(), "{}").expectStatus().isOk();
    var byOrder = "?status=REQUESTED&orderId=" + shipment.orderId();

    var page0 = list(STAFF, byOrder + "&size=2");
    var page1 = list(STAFF, byOrder + "&size=2&page=1");

    assertThat(page0.items()).extracting(RmaView::id).containsExactly(first.id(), second.id());
    assertThat(page0.total()).isEqualTo(3);
    assertThat(page1.items()).extracting(RmaView::id).containsExactly(third.id());
    assertThat(page1.page()).isEqualTo(1);
    assertThat(page1.size()).isEqualTo(2);
    assertThat(list(STAFF, "?status=APPROVED&orderId=" + shipment.orderId()).items())
        .extracting(RmaView::id)
        .containsExactly(fourth.id());
  }

  @Test
  void staffListSeveralStatusesAndTheApprovedBySendBackDeadline() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 3, "NONE").delivered(RECENTLY);
    awaitShipmentsProjected();
    var first = requestedAt(Duration.ofHours(1), shipment.orderId(), charger);
    var second = requestedAt(Duration.ofHours(2), shipment.orderId(), charger);
    var third = requestedAt(Duration.ofHours(3), shipment.orderId(), charger);
    // The later approval comes first, so the earlier one has the sooner Send-back deadline.
    clock.set(TestTimeSource.NOW.plus(Duration.ofDays(2)));
    approve(STAFF, first.id(), "{}").expectStatus().isOk();
    clock.set(TestTimeSource.NOW.plus(Duration.ofDays(1)));
    approve(STAFF, second.id(), "{}").expectStatus().isOk();
    var byOrder = "&orderId=" + shipment.orderId();

    assertThat(list(STAFF, "?status=APPROVED" + byOrder).items())
        .extracting(RmaView::id)
        .containsExactly(second.id(), first.id());
    assertThat(list(STAFF, "?status=APPROVED,REQUESTED" + byOrder).items())
        .extracting(RmaView::id)
        .containsExactly(first.id(), second.id(), third.id());
    assertThat(list(STAFF, "?status=REQUESTED&status=REJECTED" + byOrder).items())
        .extracting(RmaView::id)
        .containsExactly(third.id());
  }

  @Test
  void aMalformedListingNamesTheParameter() {
    for (var query : new String[] {"?status=OPEN", "?size=0", "?size=101", "?page=-1"}) {
      http.get()
          .uri("/rmas" + query)
          .headers(h -> h.setBearerAuth(STAFF))
          .exchange()
          .expectStatus()
          .isBadRequest();
    }
  }

  private RmaView requestedAt(Duration later, String orderId, String variantId) {
    clock.set(TestTimeSource.NOW.plus(later));
    return body(request(CUSTOMER, rma(orderId, variantId, 1, "RETURN")).expectStatus().isCreated());
  }
}
