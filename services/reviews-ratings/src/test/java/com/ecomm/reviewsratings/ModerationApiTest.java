package com.ecomm.reviewsratings;

import static com.ecomm.reviewsratings.Events.order;
import static com.ecomm.reviewsratings.Events.product;
import static com.ecomm.reviewsratings.ReviewsClient.content;
import static com.ecomm.reviewsratings.ReviewsClient.newCustomer;
import static com.ecomm.reviewsratings.ReviewsClient.newSku;
import static com.ecomm.reviewsratings.ReviewsClient.report;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ecomm.reviewsratings.ReviewsClient.Customer;
import com.ecomm.reviewsratings.ReviewsClient.HistoryEntry;
import com.ecomm.reviewsratings.ReviewsClient.Moderated;
import com.ecomm.reviewsratings.ReviewsClient.ModeratedReport;
import com.ecomm.reviewsratings.ReviewsClient.Problem;
import com.ecomm.reviewsratings.ReviewsClient.Report;
import com.ecomm.reviewsratings.ReviewsClient.Review;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A review whose text trips the screen is held until Staff look at it. Only published reviews are
 * shown and counted; the author sees their own whatever its status.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ModerationApiTest {

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
    product(sku, sku + "-V").publish();
  }

  @Test
  void aReviewWithAScreenedWordIsHeldAndNeitherShownNorCounted() {
    var published = reviews.post(boughtBy(), sku, content(5, null, "Works well."));
    var author = boughtBy();

    var held = reviews.post(author, sku, content(1, "Total SCÁM", "Avoid."));

    assertThat(published.status()).isEqualTo("PUBLISHED");
    assertThat(held.status()).isEqualTo("HELD");
    assertThat(reviews.reviews(sku, "").items()).containsExactly(published);
    assertThat(reviews.summary(sku).count()).isEqualTo(1);
    var own = reviews.eligibility(author, sku);
    assertThat(own.reason()).isEqualTo("alreadyReviewed");
    assertThat(own.review()).isEqualTo(held);
  }

  @Test
  void editingRunsTheScreenAgain() {
    var author = boughtBy();
    var review = reviews.post(author, sku, content(4, null, "Does the job."));

    var held = edited(author, review.id(), content(1, null, "Free money if you click here"));
    assertThat(held.status()).isEqualTo("HELD");
    assertThat(reviews.reviews(sku, "").total()).isZero();
    assertThat(reviews.summary(sku).count()).isZero();

    var published = edited(author, review.id(), content(2, null, "Calmer now: it's just slow."));
    assertThat(published.status()).isEqualTo("PUBLISHED");
    assertThat(reviews.reviews(sku, "").items()).containsExactly(published);
  }

  @Test
  void anotherCustomerReportsAReviewOnceAndItStaysUp() {
    var review = reviews.post(boughtBy(), sku, content(1, null, "Rubbish, all of it."));
    var reporter = newCustomer();

    var first =
        reviews
            .report(reporter, review.id(), report("OFFENSIVE", " Rude to the seller. "))
            .expectStatus()
            .isCreated()
            .expectBody(Report.class)
            .returnResult()
            .getResponseBody();
    var again =
        reviews
            .report(reporter, review.id(), report("SPAM", null))
            .expectStatus()
            .isOk()
            .expectBody(Report.class)
            .returnResult()
            .getResponseBody();

    assertThat(first.reason()).isEqualTo("OFFENSIVE");
    assertThat(first.note()).isEqualTo("Rude to the seller.");
    assertThat(first.reportedAt()).isNotNull();
    assertThat(again).isEqualTo(first);
    assertThat(reviews.reviews(sku, "").items())
        .extracting(Review::id)
        .containsExactly(review.id());
    assertThat(reviews.summary(sku).count()).isEqualTo(1);
  }

  @Test
  void reportsMadeAtOnceAreAllKept() throws Exception {
    var review = reviews.post(boughtBy(), sku, content(1, null, "Rubbish."));
    var reporters = IntStream.range(0, 12).mapToObj(i -> newCustomer()).toList();

    try (var threads = Executors.newFixedThreadPool(reporters.size())) {
      var start = new CountDownLatch(1);
      var reported = new ArrayList<Future<?>>();
      for (var reporter : reporters) {
        reported.add(
            threads.submit(
                () -> {
                  start.await();
                  reviews
                      .report(reporter, review.id(), report("SPAM", null))
                      .expectStatus()
                      .isCreated();
                  return null;
                }));
      }
      start.countDown();
      for (var future : reported) {
        future.get();
      }
    }

    assertThat(reviews.moderated(review.id()).openReports().get("SPAM")).isEqualTo(12);
  }

  @Test
  void anAuthorMayNotReportTheirOwnReview() {
    var author = boughtBy();
    var review = reviews.post(author, sku, content(5, null, "Best purchase this year."));

    var problem =
        reviews
            .report(author, review.id(), report("SPAM", null))
            .expectStatus()
            .isForbidden()
            .expectBody(Problem.class)
            .returnResult()
            .getResponseBody();

    assertThat(problem.reason()).isEqualTo("ownReview");
  }

  @Test
  void aReportNeedsAKnownReasonAndANoteOfAtMost500Characters() {
    var review = reviews.post(boughtBy(), sku, content(3, null, "Average."));
    var reporter = newCustomer();

    reviews.report(reporter, review.id(), report(null, null)).expectStatus().isBadRequest();
    reviews.report(reporter, review.id(), report("RUDE", null)).expectStatus().isBadRequest();
    reviews
        .report(reporter, review.id(), report("OTHER", "n".repeat(501)))
        .expectStatus()
        .isBadRequest();
    reviews
        .report(reporter, review.id(), report("OFF_TOPIC", "n".repeat(500)))
        .expectStatus()
        .isCreated();
  }

  @Test
  void onlyAPublishedReviewMayBeReported() {
    var held = reviews.post(boughtBy(), sku, content(1, null, "A scam."));
    var reporter = newCustomer();

    reviews.report(reporter, held.id(), report("SPAM", null)).expectStatus().isNotFound();
    reviews.report(reporter, "no-such-review", report("SPAM", null)).expectStatus().isNotFound();
    http.post()
        .uri("/reviews/" + held.id() + "/reports")
        .body(report("SPAM", null))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void theQueueListsHeldAndReportedReviewsOldestFirstWithTheirOpenReportsByReason() {
    var held = reviews.post(boughtBy(), sku, content(1, null, "Total scam."));
    var reported = reviews.post(boughtBy(), sku, content(2, null, "Meh, and the seller is awful."));
    reviews.post(boughtBy(), sku, content(5, null, "Lovely."));
    var spammer = newCustomer();
    reviews.report(spammer, reported.id(), report("SPAM", null)).expectStatus().isCreated();
    reviews.report(spammer, reported.id(), report("OFFENSIVE", null)).expectStatus().isOk();
    reviews.report(newCustomer(), reported.id(), report("SPAM", null)).expectStatus().isCreated();
    reviews
        .report(newCustomer(), reported.id(), report("OFFENSIVE", "Insults the seller."))
        .expectStatus()
        .isCreated();

    var queue = reviews.queueOf(sku);

    assertThat(queue).extracting(Moderated::id).containsExactly(held.id(), reported.id());
    assertThat(queue.get(0).status()).isEqualTo("HELD");
    assertThat(queue.get(0).openReports())
        .isEqualTo(Map.of("SPAM", 0L, "OFFENSIVE", 0L, "OFF_TOPIC", 0L, "OTHER", 0L));
    var withReports = queue.get(1);
    assertThat(withReports.status()).isEqualTo("PUBLISHED");
    assertThat(withReports.customerId()).isNotBlank();
    assertThat(withReports.body()).isEqualTo("Meh, and the seller is awful.");
    assertThat(withReports.openReports())
        .isEqualTo(Map.of("SPAM", 2L, "OFFENSIVE", 1L, "OFF_TOPIC", 0L, "OTHER", 0L));
    assertThat(withReports.reports())
        .extracting(ModeratedReport::reason, ModeratedReport::note, ModeratedReport::resolved)
        .containsExactlyInAnyOrder(
            tuple("SPAM", null, false),
            tuple("SPAM", null, false),
            tuple("OFFENSIVE", "Insults the seller.", false));
    assertThat(withReports.reports())
        .extracting(ModeratedReport::customerId)
        .contains(spammer.id());
    assertThat(withReports.history()).isEmpty();
  }

  @Test
  void staffPublishAHeldReviewAndItIsShownAndCounted() {
    var held = reviews.post(boughtBy(), sku, content(4, "Not a scam", "It really works."));

    var published = moderated(reviews.moderate("staff-ada", held.id(), "publish", null));

    assertThat(published.status()).isEqualTo("PUBLISHED");
    assertThat(published.history())
        .extracting(HistoryEntry::action, HistoryEntry::staffId, HistoryEntry::reason)
        .containsExactly(tuple("PUBLISHED", "staff-ada", null));
    assertThat(published.history().getFirst().at()).isAfterOrEqualTo(held.createdAt());
    assertThat(reviews.reviews(sku, "").items()).extracting(Review::id).containsExactly(held.id());
    assertThat(reviews.summary(sku).count()).isEqualTo(1);
    assertThat(reviews.queueOf(sku)).isEmpty();
    assertThat(refused(reviews.moderate("staff-bo", held.id(), "publish", null), 409))
        .isEqualTo("notHeld");
  }

  @Test
  void staffDismissAPublishedReviewsReportsAndItLeavesTheQueue() {
    var review = reviews.post(boughtBy(), sku, content(3, null, "Fine, I suppose."));
    reviews
        .report(newCustomer(), review.id(), report("OFF_TOPIC", null))
        .expectStatus()
        .isCreated();

    var dismissed = moderated(reviews.moderate("staff-ada", review.id(), "dismiss-reports", null));

    assertThat(dismissed.status()).isEqualTo("PUBLISHED");
    assertThat(dismissed.reports()).extracting(ModeratedReport::resolved).containsExactly(true);
    assertThat(dismissed.openReports().get("OFF_TOPIC")).isZero();
    assertThat(dismissed.history())
        .extracting(HistoryEntry::action, HistoryEntry::staffId)
        .containsExactly(tuple("REPORTS_DISMISSED", "staff-ada"));
    assertThat(reviews.queueOf(sku)).isEmpty();
    assertThat(reviews.reviews(sku, "").total()).isEqualTo(1);
    assertThat(refused(reviews.moderate("staff-ada", review.id(), "dismiss-reports", null), 409))
        .isEqualTo("noOpenReports");

    reviews.report(newCustomer(), review.id(), report("SPAM", null)).expectStatus().isCreated();
    assertThat(reviews.queueOf(sku)).extracting(Moderated::id).containsExactly(review.id());
  }

  @Test
  void staffRemoveAReviewWithAReasonItsAuthorSees() {
    var author = boughtBy();
    var review = reviews.post(author, sku, content(1, null, "The seller is a crook."));
    reviews
        .report(newCustomer(), review.id(), report("OFFENSIVE", null))
        .expectStatus()
        .isCreated();

    reviews.moderate("staff-ada", review.id(), "remove", null).expectStatus().isBadRequest();
    reviews
        .moderate("staff-ada", review.id(), "remove", Map.of("reason", " "))
        .expectStatus()
        .isBadRequest();
    reviews
        .moderate("staff-ada", review.id(), "remove", Map.of("reason", "r".repeat(1001)))
        .expectStatus()
        .isBadRequest();
    var removed =
        moderated(
            reviews.moderate(
                "staff-ada", review.id(), "remove", Map.of("reason", " Insults the seller. ")));

    assertThat(removed.status()).isEqualTo("REMOVED");
    assertThat(removed.reports()).extracting(ModeratedReport::resolved).containsExactly(true);
    assertThat(removed.history())
        .extracting(HistoryEntry::action, HistoryEntry::staffId, HistoryEntry::reason)
        .containsExactly(tuple("REMOVED", "staff-ada", "Insults the seller."));
    assertThat(reviews.reviews(sku, "").total()).isZero();
    assertThat(reviews.summary(sku).count()).isZero();
    assertThat(reviews.queueOf(sku)).isEmpty();
    assertThat(reviews.moderated(review.id()).status()).isEqualTo("REMOVED");
    assertThat(
            refused(
                reviews.moderate("staff-bo", review.id(), "remove", Map.of("reason", "Again")),
                409))
        .isEqualTo("alreadyRemoved");
    assertThat(refused(reviews.moderate("staff-bo", review.id(), "publish", null), 409))
        .isEqualTo("notHeld");
  }

  @Test
  void aRemovedReviewIsTheAuthorsOneReviewUntilTheyDeleteIt() {
    var author = boughtBy();
    var review = reviews.post(author, sku, content(1, null, "Awful people."));
    reviews
        .moderate("staff-ada", review.id(), "remove", Map.of("reason", "Abusive"))
        .expectStatus()
        .isOk();

    var own = reviews.eligibility(author, sku);
    assertThat(own.reason()).isEqualTo("alreadyReviewed");
    assertThat(own.review().status()).isEqualTo("REMOVED");
    assertThat(own.review().removalReason()).isEqualTo("Abusive");
    assertThat(reviews.postRefused(author, sku, content(2, null, "Again"), 409).reason())
        .isEqualTo("alreadyReviewed");
    var problem =
        reviews
            .edit(author, review.id(), content(2, null, "Sorry, calmer now."))
            .expectStatus()
            .isEqualTo(409)
            .expectBody(Problem.class)
            .returnResult()
            .getResponseBody();
    assertThat(problem.reason()).isEqualTo("reviewRemoved");

    reviews.delete(author, review.id()).expectStatus().isNoContent();
    var again = reviews.post(author, sku, content(2, null, "Sorry, calmer now."));
    assertThat(again.status()).isEqualTo("PUBLISHED");
    assertThat(again.removalReason()).isNull();
  }

  @Test
  void onlyStaffModerate() {
    var held = reviews.post(boughtBy(), sku, content(1, null, "A scam."));
    var customer = newCustomer();
    for (var path : new String[] {"/moderation/reviews", "/moderation/reviews/" + held.id()}) {
      http.get()
          .uri(path)
          .header("Authorization", "Bearer " + customer.token())
          .exchange()
          .expectStatus()
          .isForbidden();
      http.get().uri(path).exchange().expectStatus().isUnauthorized();
    }
    for (var action : new String[] {"publish", "dismiss-reports", "remove"}) {
      http.post()
          .uri("/reviews/" + held.id() + "/" + action)
          .header("Authorization", "Bearer " + customer.token())
          .body(Map.of("reason", "x"))
          .exchange()
          .expectStatus()
          .isForbidden();
    }
    reviews.moderated(held.id());
    http.get()
        .uri("/moderation/reviews/no-such-review")
        .header("Authorization", "Bearer " + ReviewsClient.staff("staff-1"))
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void aReviewFromBeforeModerationIsPublished() {
    var author = boughtBy();
    TestInfrastructure.insertSprint5Review(
        new Document("_id", "sprint-5-" + sku)
            .append("sku", sku)
            .append("customerId", author.id())
            .append("author", "Demo C.")
            .append("variantId", sku + "-V")
            .append("rating", 4)
            .append("title", null)
            .append("body", "From before the upgrade.")
            .append("createdAt", new Date())
            .append("editedAt", null));

    var page = reviews.reviews(sku, "");

    assertThat(page.items()).extracting(Review::id).containsExactly("sprint-5-" + sku);
    assertThat(page.items().getFirst().status()).isEqualTo("PUBLISHED");
    assertThat(reviews.summary(sku).count()).isEqualTo(1);
    var edited =
        edited(author, "sprint-5-" + sku, content(5, null, "Still good after the upgrade."));
    assertThat(edited.status()).isEqualTo("PUBLISHED");
    assertThat(reviews.reviews(sku, "").items()).containsExactly(edited);
  }

  private Moderated moderated(RestTestClient.ResponseSpec response) {
    return response
        .expectStatus()
        .isOk()
        .expectBody(Moderated.class)
        .returnResult()
        .getResponseBody();
  }

  /** The {@code reason} of a refused moderation action, once its status is checked. */
  private String refused(RestTestClient.ResponseSpec response, int status) {
    return response
        .expectStatus()
        .isEqualTo(status)
        .expectBody(Problem.class)
        .returnResult()
        .getResponseBody()
        .reason();
  }

  private Review edited(Customer author, String reviewId, Map<String, Object> content) {
    return reviews
        .edit(author, reviewId, content)
        .expectStatus()
        .isOk()
        .expectBody(Review.class)
        .returnResult()
        .getResponseBody();
  }

  /** A new Customer with a paid Order for the Product, once Reviews knows of it. */
  private Customer boughtBy() {
    var customer = newCustomer();
    order(customer.id()).line(sku + "-V").at(2, "PAID").publish();
    reviews.awaitEligibility(customer, sku, e -> e.eligible());
    return customer;
  }
}
