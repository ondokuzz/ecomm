package com.ecomm.reviewsratings;

import static org.awaitility.Awaitility.await;

import com.ecomm.commons.security.FakeKeycloak;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Test-only: the Reviews API as a client sees it, independent of the service's own classes. */
final class ReviewsClient {

  record Review(
      String id,
      String sku,
      String author,
      String variantId,
      int rating,
      String title,
      String body,
      Instant createdAt,
      Instant editedAt,
      String status,
      String removalReason) {}

  record ReviewPage(List<Review> items, int page, int size, long total) {}

  record Summary(String sku, long count, BigDecimal average, Map<String, Long> perStar) {}

  record Eligibility(boolean eligible, String reason, Review review) {}

  record Problem(int status, String reason, String detail) {}

  record Report(String reason, String note, Instant reportedAt) {}

  /**
   * A review as Staff moderate it, with its author's Customer ID, reports and moderation history.
   */
  record Moderated(
      String id,
      String sku,
      String customerId,
      String author,
      String variantId,
      int rating,
      String title,
      String body,
      Instant createdAt,
      Instant editedAt,
      String status,
      Map<String, Long> openReports,
      List<ModeratedReport> reports,
      List<HistoryEntry> history) {}

  record ModeratedReport(
      String customerId, String reason, String note, Instant reportedAt, boolean resolved) {}

  record HistoryEntry(String action, String staffId, Instant at, String reason) {}

  record ModerationPage(List<Moderated> items, int page, int size, long total) {}

  /** A Customer, with the token they sign in with. */
  record Customer(String id, String token) {}

  private final RestTestClient http;

  ReviewsClient(RestTestClient http) {
    this.http = http;
  }

  /** A Customer no other test uses, named as the realm's demo Customer is. */
  static Customer newCustomer() {
    return newCustomer("Demo", "Customer");
  }

  static Customer newCustomer(String givenName, String familyName) {
    var id = UUID.randomUUID().toString();
    return new Customer(
        id,
        FakeKeycloak.tokenWithClaims(
            id, Map.of("given_name", givenName, "family_name", familyName), "CUSTOMER"));
  }

  /** A SKU no other test uses. */
  static String newSku() {
    return "SKU-" + UUID.randomUUID().toString().substring(0, 8);
  }

  static Map<String, Object> content(int rating, String title, String body) {
    var content = new java.util.HashMap<String, Object>();
    content.put("rating", rating);
    content.put("title", title);
    content.put("body", body);
    return content;
  }

  ReviewPage reviews(String sku, String query) {
    return http.get()
        .uri("/products/" + sku + "/reviews" + (query.isEmpty() ? "" : "?" + query))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ReviewPage.class)
        .returnResult()
        .getResponseBody();
  }

  Summary summary(String sku) {
    return http.get()
        .uri("/products/" + sku + "/rating-summary")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(Summary.class)
        .returnResult()
        .getResponseBody();
  }

  Eligibility eligibility(Customer customer, String sku) {
    return http.get()
        .uri("/products/" + sku + "/eligibility")
        .header("Authorization", "Bearer " + customer.token())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(Eligibility.class)
        .returnResult()
        .getResponseBody();
  }

  /** The Customer's eligibility once it satisfies {@code until}, as events take a moment. */
  Eligibility awaitEligibility(Customer customer, String sku, Predicate<Eligibility> until) {
    return await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> eligibility(customer, sku), until);
  }

  Review post(Customer customer, String sku, Map<String, Object> content) {
    return http.post()
        .uri("/products/" + sku + "/reviews")
        .header("Authorization", "Bearer " + customer.token())
        .body(content)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(Review.class)
        .returnResult()
        .getResponseBody();
  }

  Problem postRefused(Customer customer, String sku, Map<String, Object> content, int status) {
    return http.post()
        .uri("/products/" + sku + "/reviews")
        .header("Authorization", "Bearer " + customer.token())
        .body(content)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.valueOf(status))
        .expectBody(Problem.class)
        .returnResult()
        .getResponseBody();
  }

  RestTestClient.ResponseSpec edit(
      Customer customer, String reviewId, Map<String, Object> content) {
    return http.put()
        .uri("/reviews/" + reviewId)
        .header("Authorization", "Bearer " + customer.token())
        .body(content)
        .exchange();
  }

  static Map<String, Object> report(String reason, String note) {
    var report = new java.util.HashMap<String, Object>();
    report.put("reason", reason);
    report.put("note", note);
    return report;
  }

  RestTestClient.ResponseSpec report(Customer customer, String reviewId, Map<String, Object> body) {
    return http.post()
        .uri("/reviews/" + reviewId + "/reports")
        .header("Authorization", "Bearer " + customer.token())
        .body(body)
        .exchange();
  }

  /** A Staff member's token, under {@code staffId}. */
  static String staff(String staffId) {
    return FakeKeycloak.token(staffId, "STAFF");
  }

  ModerationPage queue(String query) {
    return http.get()
        .uri("/moderation/reviews" + (query.isEmpty() ? "" : "?" + query))
        .header("Authorization", "Bearer " + staff("staff-queue"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ModerationPage.class)
        .returnResult()
        .getResponseBody();
  }

  /**
   * The queue's reviews of {@code sku}, in its order, read a page at a time, since other tests'
   * reviews are in it too.
   */
  List<Moderated> queueOf(String sku) {
    var found = new java.util.ArrayList<Moderated>();
    for (int page = 0; ; page++) {
      var items = queue("page=" + page + "&size=50").items();
      items.stream().filter(m -> m.sku().equals(sku)).forEach(found::add);
      if (items.size() < 50) {
        return found;
      }
    }
  }

  Moderated moderated(String reviewId) {
    return http.get()
        .uri("/moderation/reviews/" + reviewId)
        .header("Authorization", "Bearer " + staff("staff-reader"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(Moderated.class)
        .returnResult()
        .getResponseBody();
  }

  /**
   * Staff's {@code action} on the review: publish, dismiss-reports or remove, with {@code body}.
   */
  RestTestClient.ResponseSpec moderate(
      String staffId, String reviewId, String action, Map<String, Object> body) {
    var request =
        http.post()
            .uri("/reviews/" + reviewId + "/" + action)
            .header("Authorization", "Bearer " + staff(staffId));
    return (body == null ? request : request.body(body)).exchange();
  }

  RestTestClient.ResponseSpec delete(Customer customer, String reviewId) {
    return http.delete()
        .uri("/reviews/" + reviewId)
        .header("Authorization", "Bearer " + customer.token())
        .exchange();
  }
}
