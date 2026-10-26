package com.ecomm.recommendations;

import static com.ecomm.recommendations.Events.inStock;
import static com.ecomm.recommendations.Events.order;
import static com.ecomm.recommendations.Events.product;
import static com.ecomm.recommendations.Events.stock;
import static com.ecomm.recommendations.RecommendationsClient.newSku;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Product none of whose current Variants is in Stock is never recommended. A Variant is in Stock
 * when Inventory stocks it and has some available; one with no Stock event yet is out of Stock.
 * Recommendations keeps the newest version of each Variant's Stock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StockApiTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;
  RecommendationsClient recommendations;

  @BeforeEach
  void setUp() {
    recommendations = new RecommendationsClient(http);
  }

  @Test
  void aProductWithNoStockEventYetIsLeftOutUntilItIsInStock() {
    var p = newProduct();
    var other = newSku();
    product(other, other + "-V").publish();
    order(p + "-V", other + "-V").at(2, "PAID").publish();

    assertThat(skusOnceApplied(p)).doesNotContain(other);

    inStock(other + "-V");
    recommendations.awaitAlsoBought(p, a -> a.skus().contains(other));
  }

  @Test
  void aProductWhoseStockRunsOutIsLeftOut() {
    var p = newProduct();
    var other = newProduct();
    order(p + "-V", other + "-V").at(2, "PAID").publish();
    recommendations.awaitAlsoBought(p, a -> a.skus().contains(other));

    stock(other + "-V", 0).version(2).publish();

    recommendations.awaitAlsoBought(p, a -> !a.skus().contains(other));
  }

  @Test
  void aVariantInventoryNoLongerStocksIsOutOfStock() {
    var p = newProduct();
    var other = newProduct();
    order(p + "-V", other + "-V").at(2, "PAID").publish();
    recommendations.awaitAlsoBought(p, a -> a.skus().contains(other));

    stock(other + "-V", 5).version(2).unstocked().publish();

    recommendations.awaitAlsoBought(p, a -> !a.skus().contains(other));
  }

  @Test
  void oneVariantInStockIsEnough() {
    var p = newProduct();
    var other = newSku();
    product(other, other + "-RED", other + "-BLUE").publish();
    stock(other + "-RED", 0).publish();
    inStock(other + "-BLUE");

    order(p + "-V", other + "-RED").at(2, "PAID").publish();

    recommendations.awaitAlsoBought(p, a -> a.skus().contains(other));
  }

  @Test
  void theStockOfAVariantTheProductHasDroppedDoesNotCount() {
    var p = newProduct();
    var other = newSku();
    product(other, other + "-OLD", other + "-NEW").publish();
    inStock(other + "-OLD");
    order(p + "-V", other + "-NEW").at(2, "PAID").publish();
    recommendations.awaitAlsoBought(p, a -> a.skus().contains(other));

    product(other, other + "-NEW").version(2).publish();

    recommendations.awaitAlsoBought(p, a -> !a.skus().contains(other));
  }

  @Test
  void aStaleOrDuplicateStockEventIsIgnored() {
    var p = newProduct();
    var other = newSku();
    product(other, other + "-V").publish();
    stock(other + "-V", 0).version(3).publish();
    order(p + "-V", other + "-V").at(2, "PAID").publish();

    stock(other + "-V", 5).version(3).publish();
    stock(other + "-V", 5).version(2).publish();

    assertThat(skusOnceApplied(p)).doesNotContain(other);
  }

  /** A new Product, with one Variant, {@code <sku>-V}, in Stock. */
  private static String newProduct() {
    var sku = newSku();
    product(sku, sku + "-V").publish();
    inStock(sku + "-V");
    return sku;
  }

  /**
   * What was bought with {@code p} once every event sent so far is applied, without the marker
   * Product it waits for (as in {@link ProjectionApiTest}).
   */
  private List<String> skusOnceApplied(String p) {
    var marker = newProduct();
    order(p + "-V", marker + "-V").at(2, "PAID").publish();
    return recommendations.awaitAlsoBought(p, a -> a.skus().contains(marker)).skus().stream()
        .filter(sku -> !sku.equals(marker))
        .toList();
  }
}
