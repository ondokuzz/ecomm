package com.ecomm.searchdiscovery;

import static com.ecomm.searchdiscovery.Events.product;
import static com.ecomm.searchdiscovery.SearchClient.newCategory;
import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Product stored before Sprint 7 has no Warranty terms, as Search didn't project them. Its event,
 * read again from the start, is applied at the version held, and gives it them. The Sprint 6
 * document is made by unsetting the field in Mongo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class UpgradeApiTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;
  @Autowired MongoTemplate mongo;
  SearchClient search;
  String sku;

  @BeforeEach
  void setUp() {
    search = new SearchClient(http);
    sku = "UP-" + newCategory();
  }

  @Test
  void aProductStoredBeforeWarrantyTermsLearnsThemFromTheSameVersion() {
    product(sku).version(4).name("Pixel 9").warranty(24, "Google").price(79900, "EUR").publish();
    search.awaitProduct(sku, p -> p.warranty() != null);
    unsetWarranty(sku);
    assertThat(search.awaitProduct(sku, p -> true).warranty()).isNull();

    // Replayed, the same version of the Product is applied again, now that it has no terms.
    product(sku).version(4).name("Pixel 9").warranty(24, "Google").price(79900, "EUR").publish();

    var product = search.awaitProduct(sku, p -> p.warranty() != null);
    assertThat(product.warranty()).isEqualTo(new SearchClient.Warranty(24, "Google"));
  }

  private void unsetWarranty(String sku) {
    mongo
        .getCollection("products")
        .updateOne(new Document("_id", sku), new Document("$unset", new Document("warranty", "")));
  }
}
