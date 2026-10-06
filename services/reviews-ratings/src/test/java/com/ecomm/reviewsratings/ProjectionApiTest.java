package com.ecomm.reviewsratings;

import static com.ecomm.reviewsratings.Events.order;
import static com.ecomm.reviewsratings.Events.product;
import static com.ecomm.reviewsratings.ReviewsClient.content;
import static com.ecomm.reviewsratings.ReviewsClient.newCustomer;
import static com.ecomm.reviewsratings.ReviewsClient.newSku;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Reviews keeps the newest version of each Order and Product it has been sent, so a duplicate or
 * stale event changes nothing, and learns the two in either order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ProjectionApiTest {

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
  void aStaleOrDuplicateOrderEventIsIgnored() {
    var sku = newSku();
    product(sku, sku + "-V").publish();
    var customer = newCustomer();
    var bought = order(customer.id()).line(sku + "-V");
    bought.at(3, "PAID").publish();
    reviews.awaitEligibility(customer, sku, e -> e.eligible());

    bought.at(3, "CANCELLED").publish();
    bought.at(2, "CANCELLED").publish();
    bought.at(1, "PLACED").publish();
    awaitOrdersApplied(sku);

    assertThat(reviews.eligibility(customer, sku).eligible()).isTrue();
  }

  @Test
  void aStaleOrDuplicateProductEventIsIgnored() {
    var sku = newSku();
    product(sku, sku + "-NEW").version(3).publish();
    product(sku, sku + "-DUP").version(3).publish();
    product(sku, sku + "-OLD").version(2).publish();
    var customer = newCustomer();
    order(customer.id()).line(sku + "-NEW").at(2, "PAID").publish();
    reviews.awaitEligibility(customer, sku, e -> e.eligible());

    var other = newCustomer();
    order(other.id()).line(sku + "-OLD").line(sku + "-DUP").at(2, "PAID").publish();
    awaitOrdersApplied(sku);

    assertThat(reviews.eligibility(other, sku).reason()).isEqualTo("notPurchased");
  }

  @Test
  void anOrderArrivingBeforeItsProductCountsOnceTheProductDoes() {
    var sku = newSku();
    var customer = newCustomer();
    order(customer.id()).line(sku + "-V").at(2, "PAID").publish();

    product(sku, sku + "-V").publish();

    reviews.awaitEligibility(customer, sku, e -> e.eligible());
  }

  @Test
  void aProductsNewVariantCountsFromItsNextEvent() {
    var sku = newSku();
    product(sku, sku + "-A").publish();
    var customer = newCustomer();
    order(customer.id()).line(sku + "-B").at(2, "PAID").publish();
    awaitOrdersApplied(sku);
    assertThat(reviews.eligibility(customer, sku).eligible()).isFalse();

    product(sku, sku + "-A", sku + "-B").version(2).publish();

    reviews.awaitEligibility(customer, sku, e -> e.eligible());
    assertThat(reviews.post(customer, sku, content(5, null, "B!")).variantId())
        .isEqualTo(sku + "-B");
  }

  @Test
  void aVariantCatalogDropsStillCountsForThoseWhoBoughtIt() {
    var sku = newSku();
    product(sku, sku + "-A", sku + "-OLD").publish();
    var customer = newCustomer();
    order(customer.id()).line(sku + "-OLD").at(2, "PAID").publish();
    reviews.awaitEligibility(customer, sku, e -> e.eligible());

    product(sku, sku + "-A").version(2).publish();
    product(sku, sku + "-A").version(3).removed().publish();
    awaitOrdersApplied(sku);

    assertThat(reviews.post(customer, sku, content(4, null, "Still mine")).variantId())
        .isEqualTo(sku + "-OLD");
  }

  /**
   * Events on one partition are handled in order, so a later Order applied means all are; it waits
   * for {@code sku}'s Product too, which must have a {@code -V}, {@code -A} or {@code -NEW}
   * Variant.
   */
  private void awaitOrdersApplied(String sku) {
    var marker = newCustomer();
    order(marker.id()).line(sku + "-V").line(sku + "-A").line(sku + "-NEW").at(2, "PAID").publish();
    reviews.awaitEligibility(marker, sku, e -> e.eligible());
  }
}
