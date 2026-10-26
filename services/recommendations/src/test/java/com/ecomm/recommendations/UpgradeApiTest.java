package com.ecomm.recommendations;

import static com.ecomm.recommendations.Events.category;
import static com.ecomm.recommendations.Events.inStock;
import static com.ecomm.recommendations.Events.order;
import static com.ecomm.recommendations.Events.product;
import static com.ecomm.recommendations.RecommendationsClient.Item.popularIn;
import static com.ecomm.recommendations.RecommendationsClient.newSku;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Product kept before Sprint 6 has no Category. Its event, read again from the start, is applied
 * at the version held, and gives it one. The Sprint 5 row is written with SQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class UpgradeApiTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;
  @Autowired JdbcClient jdbc;
  RecommendationsClient recommendations;

  @BeforeEach
  void setUp() {
    recommendations = new RecommendationsClient(http);
  }

  @Test
  void aProductKeptWithoutACategoryLearnsItFromTheSameVersion() {
    var category = newSku().toLowerCase();
    category(category, "Tablets").publish();
    var p = newSku();
    var seller = newSku();
    for (var sku : new String[] {p, seller}) {
      product(sku, sku + "-V").in(category).version(4).publish();
      inStock(sku + "-V");
    }
    order(seller + "-V").at(2, "PAID").publish();
    recommendations.awaitAlsoBought(p, a -> a.skus().contains(seller));
    jdbc.sql("UPDATE products SET category = NULL WHERE sku = :sku").param("sku", p).update();
    assertThat(recommendations.alsoBought(p).items()).isEmpty();

    // Replayed, the same version of the Product is applied again, now that it has no Category.
    product(p, p + "-V").in(category).version(4).publish();

    var alsoBought = recommendations.awaitAlsoBought(p, a -> !a.items().isEmpty());
    assertThat(alsoBought.items()).containsExactly(popularIn("Tablets", seller, 1));
  }
}
