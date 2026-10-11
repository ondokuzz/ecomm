package com.ecomm.recommendations;

import static com.ecomm.recommendations.Events.order;
import static com.ecomm.recommendations.Events.product;
import static com.ecomm.recommendations.RecommendationsClient.newSku;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Recommendations keeps the newest version of each Order and Product it has been sent, so a
 * duplicate or stale event changes nothing, and learns the two in either order. Only Orders that
 * count, {@code PAID} or later and not {@code CANCELLED} or {@code RETURNED}, are bought together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ProjectionApiTest {

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
  void aPaidOrderCancelledLaterNoLongerCounts() {
    var p = newProduct();
    var other = newProduct();
    var bought = order(p + "-V", other + "-V").at(2, "PAID").publish();
    recommendations.awaitAlsoBought(p, a -> a.skus().contains(other));

    bought.at(3, "CANCELLED").publish();

    recommendations.awaitAlsoBought(p, a -> a.items().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLACED", "CANCELLED", "RETURNED"})
  void anOrderThatDoesNotCountIsNotBoughtTogether(String status) {
    var p = newProduct();
    var other = newProduct();

    order(p + "-V", other + "-V").at(2, status).publish();

    assertThat(skusOnceApplied(p)).doesNotContain(other);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PAID", "FULFILLED", "SHIPPED", "DELIVERED"})
  void anOrderPaidOrLaterIsBoughtTogether(String status) {
    var p = newProduct();
    var other = newProduct();

    order(p + "-V", other + "-V").at(2, status).publish();

    recommendations.awaitAlsoBought(p, a -> a.skus().contains(other));
  }

  @Test
  void aRemovedProductIsLeftOut() {
    var p = newProduct();
    var removed = newProduct();
    var kept = newProduct();
    order(p + "-V", removed + "-V", kept + "-V").at(2, "PAID").publish();
    recommendations.awaitAlsoBought(p, a -> a.items().size() == 2);

    product(removed, removed + "-V").version(2).removed().publish();

    var alsoBought = recommendations.awaitAlsoBought(p, a -> a.items().size() == 1);
    assertThat(alsoBought.skus()).containsExactly(kept);
  }

  @Test
  void anOrderArrivingBeforeItsProductsCountsOnceTheyDo() {
    var p = newSku();
    var other = newSku();
    order(p + "-V", other + "-V").at(2, "PAID").publish();

    product(p, p + "-V").publish();
    product(other, other + "-V").publish();

    recommendations.awaitAlsoBought(p, a -> a.skus().contains(other));
    assertThat(recommendations.alsoBought(other).skus()).containsExactly(p);
  }

  @Test
  void aVariantCatalogDropsStillCountsForItsProduct() {
    var p = newProduct();
    var other = newSku();
    product(other, other + "-OLD", other + "-NEW").publish();
    order(p + "-V", other + "-OLD").at(2, "PAID").publish();
    recommendations.awaitAlsoBought(p, a -> a.skus().contains(other));

    product(other, other + "-NEW").version(2).publish();

    assertThat(skusOnceApplied(p)).contains(other);
  }

  @Test
  void aStaleOrDuplicateOrderEventIsIgnored() {
    var p = newProduct();
    var other = newProduct();
    var bought = order(p + "-V", other + "-V").at(3, "PAID").publish();
    recommendations.awaitAlsoBought(p, a -> a.skus().contains(other));

    bought.at(3, "CANCELLED").publish();
    bought.at(2, "CANCELLED").publish();
    bought.at(1, "PLACED").publish();

    assertThat(skusOnceApplied(p)).contains(other);
  }

  @Test
  void aStaleOrDuplicateProductEventIsIgnored() {
    var p = newProduct();
    var other = newSku();
    product(other, other + "-V").version(3).removed().publish();
    order(p + "-V", other + "-V").at(2, "PAID").publish();

    product(other, other + "-V").version(3).publish();
    product(other, other + "-V").version(2).publish();

    assertThat(skusOnceApplied(p)).doesNotContain(other);
  }

  /** A new Product, with one Variant, {@code <sku>-V}. */
  private static String newProduct() {
    var sku = newSku();
    product(sku, sku + "-V").publish();
    return sku;
  }

  /**
   * What was bought with {@code p} once every event sent so far is applied, without the marker
   * Product it waits for. Each test topic has one partition, handled in order, so a marker Order
   * and Product applied means every earlier event is.
   */
  private List<String> skusOnceApplied(String p) {
    var marker = newProduct();
    order(p + "-V", marker + "-V").at(2, "PAID").publish();
    return recommendations.awaitAlsoBought(p, a -> a.skus().contains(marker)).skus().stream()
        .filter(sku -> !sku.equals(marker))
        .toList();
  }
}
