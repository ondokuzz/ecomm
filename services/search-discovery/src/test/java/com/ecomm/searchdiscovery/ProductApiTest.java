package com.ecomm.searchdiscovery;

import static com.ecomm.searchdiscovery.Events.category;
import static com.ecomm.searchdiscovery.Events.product;
import static com.ecomm.searchdiscovery.Events.stock;
import static com.ecomm.searchdiscovery.Events.variant;
import static com.ecomm.searchdiscovery.SearchClient.newCategory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.searchdiscovery.SearchClient.Money;
import com.ecomm.searchdiscovery.SearchClient.Variant;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * One Product's details, from the projection: what Catalog last published of it, and whether each
 * of its Variants is in Stock as Inventory last published.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ProductApiTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;
  SearchClient search;
  String sku;

  @BeforeEach
  void setUp() {
    search = new SearchClient(http);
    sku = "PD-" + newCategory();
  }

  @Test
  void aVariantIsAnsweredInStockOnceItsStockEventIsApplied() {
    var phones = newCategory();
    category(phones).name("Phones").publish();
    product(sku)
        .name("Pixel 9")
        .category(phones)
        .attribute("brand", "Google")
        .warranty(24, "Google")
        .variant(variant(sku + "-128", 79900, "EUR").axis("storage", "128 GB"))
        .variant(variant(sku + "-256", 89900, "EUR").axis("storage", "256 GB"))
        .publish();

    var product = search.awaitProduct(sku, p -> p.category().name().equals("Phones"));
    assertThat(product.name()).isEqualTo("Pixel 9");
    assertThat(product.category()).isEqualTo(new SearchClient.Category(phones, "Phones"));
    assertThat(product.attributes()).isEqualTo(Map.of("brand", "Google"));
    assertThat(product.warranty()).isEqualTo(new SearchClient.Warranty(24, "Google"));
    assertThat(product.variants())
        .as("no Variant has Stock yet")
        .containsExactly(
            new Variant(sku + "-128", Map.of("storage", "128 GB"), new Money(79900, "EUR"), false),
            new Variant(sku + "-256", Map.of("storage", "256 GB"), new Money(89900, "EUR"), false));

    stock(sku + "-256", 1, 3);

    var stocked = search.awaitProduct(sku, p -> p.variant(sku + "-256").inStock());
    assertThat(stocked.variant(sku + "-128").inStock()).isFalse();
  }

  @Test
  void aPriceChangeIsAnsweredOnceItsCatalogEventIsApplied() {
    product(sku).price(1000, "EUR").publish();
    search.awaitProduct(sku, p -> true);

    product(sku).version(2).price(1500, "EUR").publish();

    var product =
        search.awaitProduct(sku, p -> p.variant(sku).price().equals(new Money(1500, "EUR")));
    assertThat(product.warranty()).as("it has no Warranty terms").isNull();
  }

  @Test
  void aCategoryWhoseEventHasntArrivedIsNamedByItsSlug() {
    var unnamed = newCategory();
    product(sku).category(unnamed).price(1000, "EUR").publish();

    var product = search.awaitProduct(sku, p -> true);

    assertThat(product.category()).isEqualTo(new SearchClient.Category(unnamed, unnamed));
  }

  @Test
  void aSkuTheProjectionDoesntHoldIsNotFound() {
    assertThat(search.productStatus(sku)).isEqualTo(404);
  }

  @Test
  void aRemovedProductIsNotFound() {
    product(sku).price(1000, "EUR").publish();
    search.awaitProduct(sku, p -> true);

    product(sku).version(2).price(1000, "EUR").removed().publish();

    await().atMost(Duration.ofSeconds(30)).until(() -> search.productStatus(sku) == 404);
  }
}
