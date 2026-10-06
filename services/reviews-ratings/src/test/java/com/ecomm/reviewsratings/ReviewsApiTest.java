package com.ecomm.reviewsratings;

import static com.ecomm.reviewsratings.Events.order;
import static com.ecomm.reviewsratings.Events.product;
import static com.ecomm.reviewsratings.ReviewsClient.content;
import static com.ecomm.reviewsratings.ReviewsClient.newCustomer;
import static com.ecomm.reviewsratings.ReviewsClient.newSku;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import com.ecomm.reviewsratings.ReviewsClient.Customer;
import com.ecomm.reviewsratings.ReviewsClient.Review;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Customer reviews a Product once they have an Order for one of its Variants that counts: paid,
 * and not cancelled or returned. They may then edit or delete only their own review.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ReviewsApiTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;
  ReviewsClient reviews;
  String sku;

  @BeforeEach
  void setUp() {
    reviews = new ReviewsClient(http);
    sku = newSku();
    product(sku, sku + "-RED", sku + "-BLUE").publish();
  }

  @Test
  void aCustomerWithAPaidOrderReviewsTheProductOfTheVariantTheyBought() {
    var customer = newCustomer("Ada", "lovelace");
    var bought = order(customer.id()).line("OTHER-1").line(sku + "-BLUE");
    bought.publish();
    bought.at(2, "PAID").publish();

    var eligibility = reviews.awaitEligibility(customer, sku, e -> e.eligible());
    assertThat(eligibility.reason()).isNull();
    assertThat(eligibility.review()).isNull();

    var review = reviews.post(customer, sku, content(4, " Solid ", "Battery lasts all day."));

    assertThat(review.sku()).isEqualTo(sku);
    assertThat(review.author()).isEqualTo("Ada L.");
    assertThat(review.variantId()).isEqualTo(sku + "-BLUE");
    assertThat(review.rating()).isEqualTo(4);
    assertThat(review.title()).isEqualTo("Solid");
    assertThat(review.body()).isEqualTo("Battery lasts all day.");
    assertThat(review.createdAt()).isNotNull();
    assertThat(review.editedAt()).isNull();
    assertThat(reviews.reviews(sku, "").items()).containsExactly(review);
  }

  @Test
  void aShippedOrDeliveredOrderCountsToo() {
    var customer = newCustomer();
    var bought = order(customer.id()).line(sku + "-RED");
    bought.at(4, "DELIVERED").publish();

    reviews.awaitEligibility(customer, sku, e -> e.eligible());
  }

  @Test
  void aCustomerWhoseOrderIsOnlyPlacedMayNotReview() {
    var customer = newCustomer();
    order(customer.id()).line(sku + "-RED").publish();
    awaitApplied();

    var eligibility = reviews.eligibility(customer, sku);
    assertThat(eligibility.eligible()).isFalse();
    assertThat(eligibility.reason()).isEqualTo("notPurchased");

    var problem = reviews.postRefused(customer, sku, content(5, null, "Love it"), 403);
    assertThat(problem.reason()).isEqualTo("notPurchased");
    assertThat(reviews.reviews(sku, "").total()).isZero();
  }

  @Test
  void aCancelledOrReturnedOrderNoLongerCounts() {
    var cancelled = newCustomer();
    var returned = newCustomer();
    var first = order(cancelled.id()).line(sku + "-RED");
    first.at(2, "PAID").publish();
    reviews.awaitEligibility(cancelled, sku, e -> e.eligible());
    first.at(3, "CANCELLED").publish();
    order(returned.id()).line(sku + "-RED").at(6, "RETURNED").publish();

    reviews.awaitEligibility(cancelled, sku, e -> !e.eligible());
    awaitApplied();
    assertThat(reviews.eligibility(returned, sku).reason()).isEqualTo("notPurchased");
  }

  @Test
  void aCustomerWhoNeverBoughtTheProductMayNotReview() {
    var customer = newCustomer();
    order(customer.id()).line("SOMETHING-ELSE").at(2, "PAID").publish();
    awaitApplied();

    assertThat(reviews.eligibility(customer, sku).reason()).isEqualTo("notPurchased");
    assertThat(reviews.postRefused(customer, sku, content(3, null, "Hm"), 403).reason())
        .isEqualTo("notPurchased");
  }

  @Test
  void aCustomerReviewsAProductOnlyOnce() {
    var customer = boughtBy();
    var review = reviews.post(customer, sku, content(5, null, "Great"));

    var problem = reviews.postRefused(customer, sku, content(1, null, "Again"), 409);

    assertThat(problem.reason()).isEqualTo("alreadyReviewed");
    var eligibility = reviews.eligibility(customer, sku);
    assertThat(eligibility.eligible()).isFalse();
    assertThat(eligibility.reason()).isEqualTo("alreadyReviewed");
    assertThat(eligibility.review()).isEqualTo(review);
    assertThat(reviews.reviews(sku, "").items()).containsExactly(review);
  }

  @Test
  void aReviewStaysWhenItsOrderIsLaterCancelled() {
    var customer = newCustomer();
    var bought = order(customer.id()).line(sku + "-RED");
    bought.at(2, "PAID").publish();
    reviews.awaitEligibility(customer, sku, e -> e.eligible());
    var review = reviews.post(customer, sku, content(2, null, "Meh"));

    bought.at(3, "CANCELLED").publish();
    awaitApplied();

    assertThat(reviews.reviews(sku, "").items()).containsExactly(review);
    assertThat(reviews.eligibility(customer, sku).reason()).isEqualTo("alreadyReviewed");
  }

  @Test
  void aCustomerEditsTheirOwnReview() {
    var customer = boughtBy();
    var review = reviews.post(customer, sku, content(3, "Okay", "It's fine"));

    var edited =
        reviews
            .edit(customer, review.id(), content(5, null, "Grew on me"))
            .expectStatus()
            .isOk()
            .expectBody(Review.class)
            .returnResult()
            .getResponseBody();

    assertThat(edited.id()).isEqualTo(review.id());
    assertThat(edited.rating()).isEqualTo(5);
    assertThat(edited.title()).isNull();
    assertThat(edited.body()).isEqualTo("Grew on me");
    assertThat(edited.variantId()).isEqualTo(review.variantId());
    assertThat(edited.createdAt()).isEqualTo(review.createdAt());
    assertThat(edited.editedAt()).isAfterOrEqualTo(review.createdAt());
    assertThat(reviews.reviews(sku, "").items()).containsExactly(edited);
  }

  @Test
  void aCustomerDeletesTheirOwnReviewAndMayReviewAgain() {
    var customer = boughtBy();
    var review = reviews.post(customer, sku, content(1, null, "Broke"));

    reviews.delete(customer, review.id()).expectStatus().isNoContent();

    assertThat(reviews.reviews(sku, "").total()).isZero();
    assertThat(reviews.eligibility(customer, sku).eligible()).isTrue();
    reviews.delete(customer, review.id()).expectStatus().isNotFound();
  }

  @Test
  void anotherCustomersReviewIsNotFound() {
    var author = boughtBy();
    var review = reviews.post(author, sku, content(4, null, "Mine"));
    var other = boughtBy();

    reviews.edit(other, review.id(), content(1, null, "Hijacked")).expectStatus().isNotFound();
    reviews.delete(other, review.id()).expectStatus().isNotFound();
    reviews.edit(other, "no-such-review", content(1, null, "x")).expectStatus().isNotFound();

    assertThat(reviews.reviews(sku, "").items()).containsExactly(review);
  }

  @Test
  void anInvalidReviewIsABadRequest() {
    var customer = boughtBy();

    reviews.postRefused(customer, sku, content(0, null, "Zero"), 400);
    reviews.postRefused(customer, sku, content(6, null, "Six"), 400);
    reviews.postRefused(customer, sku, content(3, "t".repeat(121), "Long title"), 400);
    reviews.postRefused(customer, sku, content(3, null, " "), 400);
    reviews.postRefused(customer, sku, content(3, null, "b".repeat(2001)), 400);

    var review = reviews.post(customer, sku, content(3, null, "Fine"));
    reviews.edit(customer, review.id(), content(9, null, "Nine")).expectStatus().isBadRequest();
  }

  @Test
  void reviewsComeNewestFirstInPages() {
    var first = reviews.post(boughtBy(), sku, content(1, null, "first"));
    var second = reviews.post(boughtBy(), sku, content(2, null, "second"));
    var third = reviews.post(boughtBy(), sku, content(3, null, "third"));

    var page0 = reviews.reviews(sku, "page=0&size=2");
    var page1 = reviews.reviews(sku, "page=1&size=2");

    assertThat(page0.items()).containsExactly(third, second);
    assertThat(page0.total()).isEqualTo(3);
    assertThat(page0.size()).isEqualTo(2);
    assertThat(page1.items()).containsExactly(first);
    assertThat(page1.page()).isEqualTo(1);
  }

  @Test
  void anyoneMayReadReviewsButWritingOrAskingNeedsACustomer() {
    http.get().uri("/products/" + sku + "/reviews").exchange().expectStatus().isOk();
    http.get().uri("/products/" + sku + "/rating-summary").exchange().expectStatus().isOk();
    http.get().uri("/rating-summaries?sku=" + sku).exchange().expectStatus().isOk();
    http.get().uri("/products/" + sku + "/eligibility").exchange().expectStatus().isUnauthorized();
    http.post()
        .uri("/products/" + sku + "/reviews")
        .body(content(5, null, "x"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
    http.post()
        .uri("/products/" + sku + "/reviews")
        .header("Authorization", "Bearer " + FakeKeycloak.token("staff-1", "STAFF"))
        .body(content(5, null, "x"))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  /** A new Customer with a paid Order for the Product, once Reviews knows of it. */
  private Customer boughtBy() {
    var customer = newCustomer();
    order(customer.id()).line(sku + "-RED").at(2, "PAID").publish();
    reviews.awaitEligibility(customer, sku, e -> e.eligible());
    return customer;
  }

  /** Events on one partition are handled in order, so a later one applied means all are handled. */
  private void awaitApplied() {
    var marker = newCustomer();
    order(marker.id()).line(sku + "-RED").at(2, "PAID").publish();
    reviews.awaitEligibility(marker, sku, e -> e.eligible());
  }
}
