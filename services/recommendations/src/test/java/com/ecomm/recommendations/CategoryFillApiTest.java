package com.ecomm.recommendations;

import static com.ecomm.recommendations.Events.category;
import static com.ecomm.recommendations.Events.inStock;
import static com.ecomm.recommendations.Events.order;
import static com.ecomm.recommendations.Events.product;
import static com.ecomm.recommendations.Events.stock;
import static com.ecomm.recommendations.RecommendationsClient.Item.boughtTogether;
import static com.ecomm.recommendations.RecommendationsClient.Item.popularIn;
import static com.ecomm.recommendations.RecommendationsClient.newSku;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
 * When fewer Products were bought together than asked for, the rest are the Product's Category's
 * best sellers over the lookback, by counting Orders, then SKU, each with the reason {@code
 * POPULAR_IN_CATEGORY} and the Category's name. The Product itself, those already listed, removed
 * Products and those out of Stock are left out.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class CategoryFillApiTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;
  RecommendationsClient recommendations;
  String category;

  @BeforeEach
  void setUp() {
    recommendations = new RecommendationsClient(http);
    category = newSku().toLowerCase();
    category(category, "Headphones " + category).publish();
  }

  @Test
  void aProductNothingWasBoughtWithShowsItsCategorysBestSellers() {
    var p = newProduct();
    var a = newProduct();
    var b = newProduct();
    var c = newProduct();
    order(b + "-V").at(2, "PAID").publish();
    order(b + "-V").at(2, "DELIVERED").publish();
    order(a + "-V").at(2, "PAID").publish();
    order(c + "-V").at(2, "PAID").publish();

    var alsoBought = recommendations.awaitAlsoBought(p, x -> x.items().size() == 3);

    var name = "Headphones " + category;
    var first = a.compareTo(c) < 0 ? a : c;
    var second = a.compareTo(c) < 0 ? c : a;
    assertThat(alsoBought.items())
        .containsExactly(
            popularIn(name, b, 2), popularIn(name, first, 1), popularIn(name, second, 1));
    assertThat(recommendations.alsoBought(p, 1).items()).containsExactly(popularIn(name, b, 2));
  }

  @Test
  void theFillComesAfterWhatWasBoughtTogetherAndLeavesOutTheProductAndThoseListed() {
    var p = newProduct();
    var together = newProduct();
    var seller = newProduct();
    order(p + "-V", together + "-V").at(2, "PAID").publish();
    order(p + "-V").at(2, "PAID").publish();
    order(seller + "-V").at(2, "PAID").publish();

    var alsoBought = recommendations.awaitAlsoBought(p, x -> x.items().size() == 2);

    assertThat(alsoBought.items())
        .containsExactly(
            boughtTogether(together, 1), popularIn("Headphones " + category, seller, 1));
  }

  @Test
  void bestSellersOutOfStockRemovedOrOnlyBoughtLongAgoAreLeftOut() {
    var p = newProduct();
    var outOfStock = newProduct();
    var removed = newProduct();
    var longAgo = newProduct();
    var kept = newProduct();
    stock(outOfStock + "-V", 0).version(2).publish();
    product(removed, removed + "-V").in(category).version(2).removed().publish();
    order(outOfStock + "-V").at(2, "PAID").publish();
    order(removed + "-V").at(2, "PAID").publish();
    order(longAgo + "-V")
        .at(2, "PAID")
        .placedAt(Instant.now().minus(Duration.ofDays(181)))
        .publish();
    order(kept + "-V").at(2, "PAID").publish();

    var alsoBought = recommendations.awaitAlsoBought(p, x -> x.skus().contains(kept));

    assertThat(alsoBought.skus()).containsExactly(kept);
  }

  @Test
  void theFillCarriesTheCategorysNewestName() {
    var p = newProduct();
    var seller = newProduct();
    order(seller + "-V").at(2, "PAID").publish();
    recommendations.awaitAlsoBought(p, x -> x.skus().contains(seller));

    category(category, "Over-ear").version(2).publish();

    recommendations.awaitAlsoBought(
        p, x -> x.items().equals(List.of(popularIn("Over-ear", seller, 1))));
  }

  @Test
  void aRemovedCategoryFillsNothing() {
    var p = newProduct();
    var seller = newProduct();
    order(seller + "-V").at(2, "PAID").publish();
    recommendations.awaitAlsoBought(p, x -> x.skus().contains(seller));

    category(category, "Gone").version(2).removed().publish();

    recommendations.awaitAlsoBought(p, x -> x.items().isEmpty());
  }

  /** A new Product in the test's Category, with one Variant, {@code <sku>-V}, in Stock. */
  private String newProduct() {
    var sku = newSku();
    product(sku, sku + "-V").in(category).publish();
    inStock(sku + "-V");
    return sku;
  }
}
