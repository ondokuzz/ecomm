package com.ecomm.searchdiscovery;

import static com.ecomm.searchdiscovery.Events.PRODUCTS;
import static com.ecomm.searchdiscovery.Events.PRODUCTS_GROUP;
import static com.ecomm.searchdiscovery.Events.product;
import static com.ecomm.searchdiscovery.SearchClient.newCategory;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.events.kafka.DeadLetterRedrive;
import com.ecomm.commons.events.kafka.DeadLetterTopics;
import java.time.Instant;
import java.util.List;
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
 * A Product event Search can't read goes to its consumer group's dead-letter topic at once, and
 * later ones are still found. A dead event redriven is applied, by version as any other.
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
  SearchClient search;
  String category;

  @BeforeEach
  void setUp() {
    search = new SearchClient(http);
    category = newCategory();
  }

  @Test
  void aProductEventWithoutVariantsIsDeadLetteredAtOnceAndLaterOnesAreStillFound() {
    var sku = "NV-" + UUID.randomUUID();
    var withoutVariants =
        JSON.writeValueAsString(
            Map.of(
                "eventId",
                UUID.randomUUID().toString(),
                "occurredAt",
                Instant.now().toString(),
                "sku",
                sku,
                "version",
                1,
                "change",
                "CREATED",
                "product",
                Map.of(
                    "name",
                    sku,
                    "category",
                    category,
                    "attributes",
                    Map.of(),
                    "images",
                    List.of(),
                    "removed",
                    false)));

    Topics.send(PRODUCTS, sku, withoutVariants, null);
    product("NV-LATER").category(category).price(1000, "EUR").publish();

    search.awaitSearch("category=" + category, r -> r.total() == 1);
    var deadLetters =
        EventBackbone.recordsKeyed(DeadLetterTopics.deadLetterTopicOf(PRODUCTS_GROUP), sku);
    assertThat(deadLetters)
        .singleElement()
        .satisfies(r -> assertThat(r.value()).isEqualTo(withoutVariants));
    assertThat(scrape().lines())
        .as("not retried")
        .noneMatch(
            line ->
                line.startsWith("ecomm_events_retried_total{")
                    && line.contains("group=\"" + PRODUCTS_GROUP + "\""));
  }

  @Test
  void aRedrivenProductEventIsFound() {
    product("RD-" + category).category(category).price(1000, "EUR").deadLetter();

    redrive();

    search.awaitSearch("category=" + category, r -> r.total() == 1);
  }

  @Test
  void aStaleRedrivenProductEventIsIgnored() {
    var sku = "SR-" + category;
    product(sku).version(2).name("Current").category(category).price(1000, "EUR").publish();
    search.awaitSearch("category=" + category, r -> r.total() == 1);

    product(sku).version(1).name("Stale").category(category).price(1000, "EUR").deadLetter();
    var marker = newCategory();
    product("MARK-" + marker).category(marker).price(1, "EUR").deadLetter();
    redrive();

    // The redrive topic's events are handled in order, so the marker found means both are.
    search.awaitSearch("category=" + marker, r -> r.total() == 1);
    assertThat(search.search("category=" + category).item(sku).name()).isEqualTo("Current");
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
    DeadLetterRedrive.redrive(EventBackbone.bootstrapServers(), PRODUCTS_GROUP);
  }
}
