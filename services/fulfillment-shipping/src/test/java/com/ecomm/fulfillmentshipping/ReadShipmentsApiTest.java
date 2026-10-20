package com.ecomm.fulfillmentshipping;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

/**
 * A Customer reads their own Order's Shipment, and their own RMA's replacement, and nobody else's.
 * Staff, and Orchestration as Staff do, read any one, and list them by status, Warehouse and kind,
 * oldest first, a page at a time.
 */
class ReadShipmentsApiTest extends ShipmentsApiTest {

  record PageView(List<ShipmentView> items, int page, int size, long total) {}

  @Test
  void aCustomerReadsTheirOwnOrdersShipment() {
    var shipment = created();

    var read = byOrder(CUSTOMER, shipment.orderId());

    assertThat(read).isEqualTo(shipment);
  }

  @Test
  void anotherCustomersShipmentIsNotFound() {
    var shipment = created();

    http.get()
        .uri("/shipments?orderId={orderId}", shipment.orderId())
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-99", "CUSTOMER")))
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void anOrderWithoutAShipmentHasNone() {
    http.get()
        .uri("/shipments?orderId={orderId}", UUID.randomUUID())
        .headers(h -> h.setBearerAuth(CUSTOMER))
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void staffAndOrchestrationReadAnyOrdersShipment() {
    var shipment = created();

    assertThat(byOrder(STAFF, shipment.orderId())).isEqualTo(shipment);
    assertThat(byOrder(ORCHESTRATION, shipment.orderId())).isEqualTo(shipment);
    assertThat(read(ORCHESTRATION, shipment.id())).isEqualTo(shipment);
  }

  @Test
  void aCustomerMayNotReadByIdOrListTheQueue() {
    var shipment = created();
    http.get()
        .uri("/shipments/{id}", shipment.id())
        .headers(h -> h.setBearerAuth(CUSTOMER))
        .exchange()
        .expectStatus()
        .isForbidden();
    http.get()
        .uri("/shipments?status=AWAITING_PICK")
        .headers(h -> h.setBearerAuth(CUSTOMER))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void theQueueListsByStatusAndWarehouseOldestFirst() {
    var amsterdamFirst = created("NL", Map.of(CASE, 1));
    var berlin = created("DE", Map.of(CASE, 1));
    var amsterdamSecond = created("NL", Map.of(CASE, 1));
    var amsterdamPicked = created("NL", Map.of(CASE, 1));
    pick(amsterdamPicked.id()).expectStatus().isOk();

    var queue = queue("status=AWAITING_PICK&warehouseId=WH-AMS&size=100");

    var ids = queue.items().stream().map(ShipmentView::id).toList();
    assertThat(ids).contains(amsterdamFirst.id(), amsterdamSecond.id());
    assertThat(ids.indexOf(amsterdamFirst.id())).isLessThan(ids.indexOf(amsterdamSecond.id()));
    assertThat(ids).doesNotContain(berlin.id(), amsterdamPicked.id());
    assertThat(queue.items())
        .allSatisfy(
            s -> {
              assertThat(s.status()).isEqualTo("AWAITING_PICK");
              assertThat(s.warehouseId()).isEqualTo("WH-AMS");
            });
    assertThat(queue("status=PICKED&warehouseId=WH-AMS&size=100").items())
        .extracting(ShipmentView::id)
        .contains(amsterdamPicked.id());
  }

  @Test
  void theQueueIsPaged() {
    created("NL", Map.of(CASE, 1));
    created("NL", Map.of(CASE, 1));
    created("NL", Map.of(CASE, 1));

    var first = queue("warehouseId=WH-AMS&page=0&size=2");
    var second = queue("warehouseId=WH-AMS&page=1&size=2");

    assertThat(first.items()).hasSize(2);
    assertThat(first.total()).isGreaterThanOrEqualTo(3);
    assertThat(second.total()).isEqualTo(first.total());
    assertThat(second.items()).doesNotContainAnyElementsOf(first.items());
  }

  @Test
  void anUnknownStatusOrABadPageIsABadRequest() {
    for (var query : List.of("status=LOST", "kind=RETURN", "size=0", "size=101", "page=-1")) {
      http.get()
          .uri("/shipments?" + query)
          .headers(h -> h.setBearerAuth(STAFF))
          .exchange()
          .expectStatus()
          .isBadRequest();
    }
  }

  @Test
  void anOrdersShipmentIsStillReadByOrderOnceItHasAReplacement() {
    var shipment = created();
    createdFrom(
        replacementRequest(
            UUID.randomUUID().toString(),
            shipment.orderId(),
            "customer-42",
            "DE",
            lines(Map.of(PHONE, 1))));

    assertThat(byOrder(CUSTOMER, shipment.orderId())).isEqualTo(shipment);
  }

  @Test
  void anRmasReplacementIsReadByItsCustomerStaffAndOrchestration() {
    var replacement = replacement();

    for (var token : List.of(CUSTOMER, STAFF, ORCHESTRATION)) {
      assertThat(byRma(token, replacement.rmaId())).isEqualTo(replacement);
    }
  }

  @Test
  void anotherCustomersReplacementLikeAnRmaWithoutOneIsNotFound() {
    var replacement = replacement();

    for (var rmaId : List.of(replacement.rmaId(), UUID.randomUUID().toString())) {
      http.get()
          .uri("/shipments?rmaId={rmaId}", rmaId)
          .headers(
              h ->
                  h.setBearerAuth(
                      rmaId.equals(replacement.rmaId())
                          ? FakeKeycloak.token("customer-99", "CUSTOMER")
                          : CUSTOMER))
          .exchange()
          .expectStatus()
          .isNotFound();
    }
  }

  @Test
  void theQueueListsByKind() {
    var shipment = created("NL", Map.of(CASE, 1));
    var replacement =
        createdFrom(
            replacementRequest(
                UUID.randomUUID().toString(),
                shipment.orderId(),
                "customer-42",
                "NL",
                lines(Map.of(CASE, 1))));

    var replacements = queue("kind=REPLACEMENT&warehouseId=WH-AMS&size=100").items();
    var orders = queue("kind=ORDER&warehouseId=WH-AMS&size=100").items();

    assertThat(replacements).extracting(ShipmentView::id).contains(replacement.id());
    assertThat(replacements).extracting(ShipmentView::kind).containsOnly("REPLACEMENT");
    assertThat(orders).extracting(ShipmentView::id).contains(shipment.id());
    assertThat(orders).extracting(ShipmentView::kind).containsOnly("ORDER");
    assertThat(queue("warehouseId=WH-AMS&size=100").items())
        .extracting(ShipmentView::id)
        .contains(shipment.id(), replacement.id());
  }

  private ShipmentView byRma(String token, String rmaId) {
    return http.get()
        .uri("/shipments?rmaId={rmaId}", rmaId)
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ShipmentView.class)
        .returnResult()
        .getResponseBody();
  }

  private ShipmentView byOrder(String token, String orderId) {
    return http.get()
        .uri("/shipments?orderId={orderId}", orderId)
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ShipmentView.class)
        .returnResult()
        .getResponseBody();
  }

  private PageView queue(String query) {
    return http.get()
        .uri("/shipments?" + query)
        .headers(h -> h.setBearerAuth(STAFF))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<PageView>() {})
        .returnResult()
        .getResponseBody();
  }
}
