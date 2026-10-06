package com.ecomm.reviewsratings;

import static com.ecomm.reviewsratings.Events.order;
import static com.ecomm.reviewsratings.Events.product;
import static com.ecomm.reviewsratings.ReviewsClient.content;
import static com.ecomm.reviewsratings.ReviewsClient.newCustomer;
import static com.ecomm.reviewsratings.ReviewsClient.newSku;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.reviewsratings.ReviewsClient.Summary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/** A Product's rating summary, alone or for many Products at once. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class RatingSummariesApiTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;
  ReviewsClient reviews;

  @BeforeEach
  void setUp() {
    reviews = new ReviewsClient(http);
  }

  @Test
  void aProductWithoutReviewsHasNoAverage() {
    var summary = reviews.summary(newSku());

    assertThat(summary.count()).isZero();
    assertThat(summary.average()).isNull();
    assertThat(summary.perStar()).isEqualTo(Map.of("1", 0L, "2", 0L, "3", 0L, "4", 0L, "5", 0L));
  }

  @Test
  void theSummaryAveragesToOneDecimalAndCountsEachStar() {
    var sku = reviewed(5, 5, 4);

    var summary = reviews.summary(sku);

    assertThat(summary.count()).isEqualTo(3);
    assertThat(summary.average()).isEqualByComparingTo(new BigDecimal("4.7"));
    assertThat(summary.perStar()).isEqualTo(Map.of("1", 0L, "2", 0L, "3", 0L, "4", 1L, "5", 2L));
  }

  @Test
  void manyProductsSummariesComeAtOnceInTheOrderAsked() {
    var first = reviewed(1, 2);
    var second = reviewed(5);
    var unreviewed = newSku();

    var summaries = batch("sku=" + second + "&sku=" + unreviewed + "&sku=" + first);

    assertThat(summaries).extracting(Summary::sku).containsExactly(second, unreviewed, first);
    assertThat(summaries.get(0).average()).isEqualByComparingTo("5.0");
    assertThat(summaries.get(1).count()).isZero();
    assertThat(summaries.get(2).average()).isEqualByComparingTo("1.5");
    assertThat(summaries.get(2).count()).isEqualTo(2);
  }

  @Test
  void upTo50SkusMayBeAskedAtOnce() {
    var skus = IntStream.range(0, 50).mapToObj(i -> "sku=" + newSku()).toList();
    assertThat(batch(String.join("&", skus))).hasSize(50);

    var tooMany = String.join("&", skus) + "&sku=" + newSku();
    http.get().uri("/rating-summaries?" + tooMany).exchange().expectStatus().isBadRequest();
    http.get().uri("/rating-summaries").exchange().expectStatus().isBadRequest();
  }

  @Test
  void aSkuAskedTwiceIsSummarizedOnce() {
    var sku = reviewed(3);
    assertThat(batch("sku=" + sku + "&sku=" + sku)).hasSize(1);
  }

  private List<Summary> batch(String query) {
    return http.get()
        .uri("/rating-summaries?" + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<List<Summary>>() {})
        .returnResult()
        .getResponseBody();
  }

  /** A new Product, reviewed once with each rating by a Customer who bought it. */
  private String reviewed(int... ratings) {
    var sku = newSku();
    product(sku, sku + "-V").publish();
    for (var rating : ratings) {
      var customer = newCustomer();
      order(customer.id()).line(sku + "-V").at(2, "PAID").publish();
      reviews.awaitEligibility(customer, sku, e -> e.eligible());
      reviews.post(customer, sku, content(rating, null, "Rated " + rating));
    }
    return sku;
  }
}
