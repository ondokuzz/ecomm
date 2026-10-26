package com.ecomm.recommendations;

import static com.ecomm.recommendations.Events.inStock;
import static com.ecomm.recommendations.Events.order;
import static com.ecomm.recommendations.Events.product;
import static com.ecomm.recommendations.RecommendationsClient.Item.boughtTogether;
import static com.ecomm.recommendations.RecommendationsClient.newSku;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
 * What Customers also bought with a Product: the other Products in the counting Orders that bought
 * it, ranked, read by anyone without a token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class AlsoBoughtApiTest {

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
  void twoProductsPaidForTogetherAreEachBoughtWithTheOther() {
    var phone = newSku();
    var case_ = newSku();
    product(phone, phone + "-V").publish();
    product(case_, case_ + "-V").publish();
    inStock(phone + "-V", case_ + "-V");

    order(phone + "-V", case_ + "-V").at(2, "PAID").publish();

    var withPhone = recommendations.awaitAlsoBought(phone, a -> !a.items().isEmpty());
    assertThat(withPhone.sku()).isEqualTo(phone);
    assertThat(withPhone.items()).containsExactly(boughtTogether(case_, 1));
    assertThat(recommendations.alsoBought(case_).items()).containsExactly(boughtTogether(phone, 1));
  }

  /**
   * By how many Orders bought them with the Product, then by the most recent such Order, then by
   * SKU; an Order counts once per Product however many of its Variants it bought.
   */
  @Test
  void productsAreRankedByOrdersThenByTheirLatestOrderThenBySku() {
    var base = newSku();
    var p = base + "-P";
    var skus = List.of(p, base + "-A", base + "-B", base + "-C", base + "-D", base + "-E");
    for (var sku : skus) {
      product(sku, sku + "-1", sku + "-2").publish();
      inStock(sku + "-1");
    }
    var now = Instant.now();
    order(p + "-1", base + "-A-1", base + "-A-2", base + "-B-1")
        .at(2, "PAID")
        .placedAt(now.minusSeconds(500))
        .publish();
    order(p + "-1", p + "-2", base + "-A-1")
        .at(2, "PAID")
        .placedAt(now.minusSeconds(400))
        .publish();
    order(p + "-2", base + "-C-1").at(2, "PAID").placedAt(now.minusSeconds(100)).publish();
    order(p + "-1", base + "-E-1", base + "-D-2")
        .at(2, "PAID")
        .placedAt(now.minusSeconds(300))
        .publish();

    var alsoBought = recommendations.awaitAlsoBought(p, a -> a.items().size() == 4);

    assertThat(alsoBought.items())
        .containsExactly(
            boughtTogether(base + "-A", 2),
            boughtTogether(base + "-C", 1),
            boughtTogether(base + "-D", 1),
            boughtTogether(base + "-E", 1));
    assertThat(recommendations.alsoBought(p, 12).skus())
        .containsExactly(base + "-A", base + "-C", base + "-D", base + "-E", base + "-B");
    assertThat(recommendations.alsoBought(p, 1).skus()).containsExactly(base + "-A");
  }

  /** Only Orders placed within {@code ecomm.recommendations.lookback}, 180 days, count. */
  @Test
  void ordersPlacedBeforeTheLookbackDoNotCount() {
    var p = newSku();
    var recent = newSku();
    var old = newSku();
    for (var sku : List.of(p, recent, old)) {
      product(sku, sku + "-V").publish();
      inStock(sku + "-V");
    }
    var now = Instant.now();
    order(p + "-V", old + "-V").at(2, "PAID").placedAt(now.minus(Duration.ofDays(181))).publish();
    order(p + "-V", old + "-V")
        .at(2, "DELIVERED")
        .placedAt(now.minus(Duration.ofDays(200)))
        .publish();
    order(p + "-V", recent + "-V")
        .at(2, "PAID")
        .placedAt(now.minus(Duration.ofDays(179)))
        .publish();

    var alsoBought = recommendations.awaitAlsoBought(p, a -> !a.items().isEmpty());

    assertThat(alsoBought.items()).containsExactly(boughtTogether(recent, 1));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "13", "-1", "four"})
  void aLimitOutsideOneToTwelveIsRefused(String limit) {
    http.get()
        .uri("/products/{sku}/also-bought?limit={limit}", newSku(), limit)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void anUnknownProductHasNothingBoughtWithIt() {
    assertThat(recommendations.alsoBought(newSku()).items()).isEmpty();
  }
}
