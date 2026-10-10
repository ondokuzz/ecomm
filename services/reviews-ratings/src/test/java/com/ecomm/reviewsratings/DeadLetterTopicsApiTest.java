package com.ecomm.reviewsratings;

import static com.ecomm.reviewsratings.Events.ORDERS;
import static com.ecomm.reviewsratings.Events.ORDERS_GROUP;
import static com.ecomm.reviewsratings.Events.order;
import static com.ecomm.reviewsratings.Events.product;
import static com.ecomm.reviewsratings.ReviewsClient.newCustomer;
import static com.ecomm.reviewsratings.ReviewsClient.newSku;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.events.kafka.DeadLetterRedrive;
import com.ecomm.commons.events.kafka.DeadLetterTopics;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * An Order event Reviews can't read goes to its consumer group's dead-letter topic at once, and
 * later ones are still applied. A dead event redriven is applied, by version as any other.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class DeadLetterTopicsApiTest {

  private static final JsonMapper JSON = new JsonMapper();

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
  void anOrderEventWithoutLinesIsDeadLetteredAtOnceAndLaterOnesAreStillApplied() {
    var sku = newSku();
    product(sku, sku + "-V").publish();
    var orderId = UUID.randomUUID().toString();
    var withoutLines =
        JSON.writeValueAsString(
            Map.of(
                "eventId",
                UUID.randomUUID().toString(),
                "occurredAt",
                Instant.now().toString(),
                "orderId",
                orderId,
                "version",
                1,
                "change",
                "PLACED",
                "order",
                Map.of(
                    "customerId",
                    "customer-" + orderId,
                    "status",
                    "PAID",
                    "placedAt",
                    Instant.now().toString())));

    Topics.send(ORDERS, orderId, withoutLines, null);
    var customer = newCustomer();
    order(customer.id()).line(sku + "-V").at(2, "PAID").publish();

    reviews.awaitEligibility(customer, sku, e -> e.eligible());
    assertThat(
            EventBackbone.recordsKeyed(DeadLetterTopics.deadLetterTopicOf(ORDERS_GROUP), orderId))
        .singleElement()
        .satisfies(r -> assertThat(r.value()).isEqualTo(withoutLines));
    assertThat(scrape().lines())
        .as("not retried")
        .noneMatch(
            line ->
                line.startsWith("ecomm_events_retried_total{")
                    && line.contains("group=\"" + ORDERS_GROUP + "\""));
  }

  @Test
  void aRedrivenOrderEventIsApplied() {
    var sku = newSku();
    product(sku, sku + "-V").publish();
    var customer = newCustomer();
    order(customer.id()).line(sku + "-V").at(2, "PAID").deadLetter();

    redrive();

    reviews.awaitEligibility(customer, sku, e -> e.eligible());
  }

  @Test
  void aStaleRedrivenOrderEventIsIgnored() {
    var sku = newSku();
    product(sku, sku + "-V").publish();
    var customer = newCustomer();
    var bought = order(customer.id()).line(sku + "-V");
    bought.at(2, "PAID").publish();
    reviews.awaitEligibility(customer, sku, e -> e.eligible());

    bought.at(1, "PLACED").deadLetter();
    var marker = newCustomer();
    order(marker.id()).line(sku + "-V").at(2, "PAID").deadLetter();
    redrive();

    // The redrive topic's events are handled in order, so the marker applied means both are.
    reviews.awaitEligibility(marker, sku, e -> e.eligible());
    assertThat(reviews.eligibility(customer, sku).eligible()).isTrue();
  }

  private String scrape() {
    return http.get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  private static void redrive() {
    DeadLetterRedrive.redrive(EventBackbone.bootstrapServers(), ORDERS_GROUP);
  }
}
