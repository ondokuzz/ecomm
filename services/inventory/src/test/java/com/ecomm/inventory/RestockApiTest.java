package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The returns Saga puts returned units back on hand: a {@code RESTOCKED} Stock movement naming the
 * RMA reference, once per {@code Idempotency-Key}. Restocking is Orchestration's alone.
 */
class RestockApiTest extends InventoryApiTest {

  private static final String RMA_REFERENCE = "RMA-3F2A9C1B";

  @Test
  void aRestockPutsTheUnitsBackOnHandWithItsReference() {
    var variant = newVariant(5);

    restock(orchestrationToken(), variant, newKey(), restockBody(2, RMA_REFERENCE))
        .expectStatus()
        .isOk()
        .expectBody(StockView.class)
        .isEqualTo(new StockView(variant, 7, 7, 0));

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 7, 7, 0));
    var ledger = ledgerOf(variant);
    assertThat(ledger.changes().getFirst())
        .isEqualTo(new MovementView("RESTOCKED", 2, 0, null, null, RMA_REFERENCE, null));
    assertThat(ledger.balanced()).isTrue();
    assertThat(ledger.onHandFromMovements()).isEqualTo(7);
  }

  @Test
  void theSameKeyTwiceRestocksOnce() {
    var variant = newVariant(5);
    var key = newKey();

    restock(orchestrationToken(), variant, key, restockBody(2, RMA_REFERENCE))
        .expectStatus()
        .isOk();
    restock(orchestrationToken(), variant, key, restockBody(2, RMA_REFERENCE))
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Idempotent-Replayed", "true");

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 7, 7, 0));
    assertThat(ledgerOf(variant).changes())
        .filteredOn(m -> m.kind().equals("RESTOCKED"))
        .hasSize(1);
  }

  @Test
  void aRestockPublishesTheVariantsStock() {
    var variant = newVariant(5);

    restock(orchestrationToken(), variant, newKey(), restockBody(3, RMA_REFERENCE))
        .expectStatus()
        .isOk();

    var records = StockEvents.keyed(variant, 2);
    assertThat(records).hasSize(2);
    var last = records.getLast();
    assertThat(StockEvents.schemaViolationsOf(last)).isEmpty();
    var event = StockEvents.valueOf(last);
    assertThat(event.get("change").asText()).isEqualTo("RESTOCKED");
    assertThat(event.at("/stock/onHand").asInt()).isEqualTo(8);
    assertThat(event.at("/stock/available").asInt()).isEqualTo(8);
  }

  @Test
  void theStockCacheFollowsARestock() {
    var variant = newVariant(1);
    // Read once, so the Stock cache holds 1 available.
    assertThat(stockOf(variant).quantity()).isEqualTo(1);

    restock(orchestrationToken(), variant, newKey(), restockBody(2, RMA_REFERENCE))
        .expectStatus()
        .isOk();

    assertThat(stockOf(variant).quantity()).isEqualTo(3);
    // The cache no longer shows too little for a Reservation of all three.
    reserved(item(variant, 3));
  }

  @Test
  void onlyOrchestrationRestocks() {
    var variant = newVariant(5);

    restock(staffToken(), variant, newKey(), restockBody(1, RMA_REFERENCE))
        .expectStatus()
        .isForbidden();
    restock(checkoutToken(), variant, newKey(), restockBody(1, RMA_REFERENCE))
        .expectStatus()
        .isForbidden();

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 5, 5, 0));
  }

  @Test
  void aRestockWithoutAKeyIsRefused() {
    var variant = newVariant(5);

    restock(orchestrationToken(), variant, null, restockBody(1, RMA_REFERENCE))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("idempotencyKeyRequired");

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 5, 5, 0));
  }

  @Test
  void aRestockNeedsAPositiveQuantityAndAReference() {
    var variant = newVariant(5);

    restock(orchestrationToken(), variant, newKey(), restockBody(0, RMA_REFERENCE))
        .expectStatus()
        .isBadRequest();
    restock(orchestrationToken(), variant, newKey(), restockBody(1, " "))
        .expectStatus()
        .isBadRequest();
    restock(orchestrationToken(), variant, newKey(), restockBody(1, "R".repeat(65)))
        .expectStatus()
        .isBadRequest();

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 5, 5, 0));
    assertThat(ledgerOf(variant).total()).isEqualTo(1);
  }

  @Test
  void aVariantInventoryDoesntStockCantBeRestocked() {
    var variant = "TEST-" + UUID.randomUUID();

    restock(orchestrationToken(), variant, newKey(), restockBody(1, RMA_REFERENCE))
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.unknownVariants[0]")
        .isEqualTo(variant);
  }

  private RestTestClient.ResponseSpec restock(
      String token, String variantId, String key, String body) {
    return http.post()
        .uri("/stock/{variantId}/restocks", variantId)
        .headers(
            h -> {
              h.setBearerAuth(token);
              if (key != null) {
                h.set("Idempotency-Key", key);
              }
            })
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private static String restockBody(int quantity, String reference) {
    return """
        {"quantity": %d, "reference": "%s"}
        """
        .formatted(quantity, reference);
  }

  private static String newKey() {
    return UUID.randomUUID().toString();
  }
}
