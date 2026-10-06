package com.ecomm.searchdiscovery;

import static com.ecomm.searchdiscovery.Events.category;
import static com.ecomm.searchdiscovery.Events.product;
import static com.ecomm.searchdiscovery.Events.stock;
import static com.ecomm.searchdiscovery.Events.variant;
import static com.ecomm.searchdiscovery.SearchClient.newCategory;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.searchdiscovery.SearchClient.CategoryCount;
import com.ecomm.searchdiscovery.SearchClient.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Search keeps the newest version of each Product, Category and Variant's Stock it has been sent,
 * so what it finds follows Catalog and Inventory, and a duplicate or stale event changes nothing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ProjectionApiTest {

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
  void aRemovedProductDisappears() {
    product("RM-1").category(category).price(1000, "EUR").publish();
    product("RM-2").category(category).price(1000, "EUR").publish();
    search.awaitSearch("category=" + category, r -> r.total() == 2);

    product("RM-1").version(2).category(category).price(1000, "EUR").removed().publish();

    var results = search.awaitSearch("category=" + category, r -> r.total() == 1);
    assertThat(results.skus()).containsExactly("RM-2");
    assertThat(results.categoryCount(category)).isEqualTo(1);
  }

  @Test
  void aPriceChangeIsReflected() {
    product("PC-1").category(category).price(1000, "EUR").publish();
    search.awaitSearch("category=" + category, r -> r.total() == 1);

    product("PC-1").version(2).category(category).price(1500, "EUR").publish();

    var results =
        search.awaitSearch(
            "category=" + category, r -> r.item("PC-1").priceFrom().equals(new Money(1500, "EUR")));
    assertThat(results.facets().prices())
        .containsExactly(new SearchClient.PriceRange("EUR", 1500, 1500));
  }

  @Test
  void aStaleOrDuplicateProductEventIsIgnored() {
    product("ST-1").version(3).name("Current").category(category).price(1000, "EUR").publish();
    search.awaitSearch("category=" + category, r -> r.total() == 1);

    product("ST-1").version(3).name("Duplicate").category(category).price(1000, "EUR").publish();
    product("ST-1").version(2).name("Stale").category(category).price(1000, "EUR").publish();
    product("ST-1").version(1).category(category).price(1000, "EUR").removed().publish();
    awaitAllHandled();

    assertThat(search.search("category=" + category).item("ST-1").name()).isEqualTo("Current");
  }

  @Test
  void aProductRemovedThenCreatedAgainIsFoundAgain() {
    product("RC-1").category(category).price(1000, "EUR").publish();
    product("RC-1").version(2).category(category).price(1000, "EUR").removed().publish();
    product("RC-1").version(3).name("Back").category(category).price(1000, "EUR").publish();

    var results = search.awaitSearch("category=" + category, r -> r.total() == 1);
    assertThat(results.item("RC-1").name()).isEqualTo("Back");
  }

  @Test
  void aProductIsInStockWhileAnyOfItsVariantsIs() {
    product("IS-1")
        .category(category)
        .variant(variant("IS-1-A", 1000, "EUR").axis("color", "Red"))
        .variant(variant("IS-1-B", 1000, "EUR").axis("color", "Blue"))
        .publish();
    search.awaitSearch("category=" + category, r -> r.total() == 1);
    assertThat(search.search("category=" + category).item("IS-1").inStock())
        .as("no Variant has Stock yet")
        .isFalse();

    stock("IS-1-B", 1, 3);
    search.awaitSearch("category=" + category, r -> r.item("IS-1").inStock());

    stock("IS-1-B", 2, 0);
    search.awaitSearch("category=" + category, r -> !r.item("IS-1").inStock());

    stock("IS-1-A", 1, 5, false);
    stock("IS-1-B", 3, 2);
    stock("IS-1-B", 2, 0);
    var results = search.awaitSearch("category=" + category, r -> r.item("IS-1").inStock());
    assertThat(results.facets().inStock()).isEqualTo(1);
  }

  @Test
  void aVariantInventoryNoLongerStocksIsOutOfStock() {
    product("NS-1").category(category).price(1000, "EUR").publish();
    stock("NS-1", 1, 4);
    search.awaitSearch("category=" + category, r -> r.total() == 1 && r.item("NS-1").inStock());

    stock("NS-1", 2, 4, false);

    search.awaitSearch("category=" + category, r -> !r.item("NS-1").inStock());
  }

  @Test
  void aCategoryIsNamedByItsLatestEventAndGoesWhenRemoved() {
    category(category).name("Old name").publish();
    category(category).version(2).name("Phones").publish();
    category(category).version(1).name("Stale").publish();
    product("CN-1").category(category).price(1000, "EUR").publish();

    var results =
        search.awaitSearch(
            "category=" + category,
            r ->
                r.total() == 1
                    && r.facets().categories().contains(new CategoryCount(category, "Phones", 1)));
    assertThat(results.total()).isEqualTo(1);

    product("CN-1").version(2).category(category).price(1000, "EUR").removed().publish();
    category(category).version(3).name("Phones").removed().publish();

    search.awaitSearch(
        "q=" + SearchClient.newWord(),
        r -> r.facets().categories().stream().noneMatch(c -> c.slug().equals(category)));
  }

  /** Events on one partition are handled in order, so a later one applied means all are handled. */
  private void awaitAllHandled() {
    var marker = newCategory();
    product("MARK-" + marker).category(marker).price(1, "EUR").publish();
    search.awaitSearch("category=" + marker, r -> r.total() == 1);
  }
}
