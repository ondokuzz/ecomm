package com.ecomm.searchdiscovery;

import static com.ecomm.searchdiscovery.Events.product;
import static com.ecomm.searchdiscovery.Events.stock;
import static com.ecomm.searchdiscovery.Events.variant;
import static com.ecomm.searchdiscovery.SearchClient.newCategory;
import static com.ecomm.searchdiscovery.SearchClient.newWord;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.searchdiscovery.SearchClient.Money;
import com.ecomm.searchdiscovery.SearchClient.Summary;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Products published by Catalog become searchable, as summaries, without a token. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class SearchApiTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;
  SearchClient search;

  @BeforeEach
  void client() {
    search = new SearchClient(http);
  }

  @Test
  void aPublishedProductIsFoundAsASummary() {
    var category = newCategory();
    product("PHN-1")
        .name("Pixel")
        .category(category)
        .attribute("brand", "Google")
        .image("/images/pixel.svg")
        .variant(variant("PHN-1", 89900, "EUR").axis("storage", "256 GB"))
        .variant(variant("PHN-1-128", 79900, "EUR").axis("storage", "128 GB"))
        .publish();

    var results = search.awaitSearch("category=" + category, r -> r.total() == 1);

    assertThat(results.items())
        .containsExactly(
            new Summary(
                "PHN-1",
                "Pixel",
                "/images/pixel.svg",
                new Money(79900, "EUR"),
                true,
                false,
                Map.of("brand", "Google")));
  }

  @Test
  void textIsLookedForInTheNameTheDescriptionAndTheAttributeValuesBestMatchFirst() {
    var word = newWord();
    product("TX-NAME").name("The " + word + " phone").price(1000, "EUR").publish();
    product("TX-DESC").name("Other").description("Made with " + word).price(1000, "EUR").publish();
    product("TX-ATTR").name("Third").attribute("brand", word).price(1000, "EUR").publish();
    product("TX-AXIS")
        .name("Fourth")
        .variant(variant("TX-AXIS", 1000, "EUR").axis("color", word))
        .publish();
    product("TX-NONE").name("Unrelated").price(1000, "EUR").publish();

    var results = search.awaitSearch("q=" + word, r -> r.total() == 4);

    assertThat(results.skus())
        .containsExactlyInAnyOrder("TX-NAME", "TX-DESC", "TX-ATTR", "TX-AXIS")
        .startsWith("TX-NAME")
        .endsWith("TX-DESC");
  }

  @Test
  void aCategoryKeepsItsOwnProducts() {
    var phones = newCategory();
    var laptops = newCategory();
    product("CT-1").category(phones).price(1000, "EUR").publish();
    product("CT-2").category(laptops).price(1000, "EUR").publish();
    product("CT-3").category(phones).price(1000, "EUR").publish();

    var results = search.awaitSearch("category=" + phones, r -> r.total() == 2);

    assertThat(results.skus()).containsExactlyInAnyOrder("CT-1", "CT-3");
  }

  @Test
  void attributeFiltersMatchAProductsOwnValueOrAnyVariantsAndAnyOfSeveralValues() {
    var category = newCategory();
    product("AT-1")
        .category(category)
        .attribute("brand", "Google")
        .variant(variant("AT-1-A", 1000, "EUR").axis("storage", "128 GB"))
        .variant(variant("AT-1-B", 1200, "EUR").axis("storage", "256 GB"))
        .publish();
    product("AT-2")
        .category(category)
        .attribute("brand", "Apple")
        .variant(variant("AT-2", 1000, "EUR").axis("storage", "512 GB"))
        .publish();
    product("AT-3")
        .category(category)
        .attribute("brand", "Google")
        .variant(variant("AT-3", 1000, "EUR").axis("storage", "512 GB"))
        .publish();
    search.awaitSearch("category=" + category, r -> r.total() == 3);

    assertThat(search.search("category=" + category + "&attr.storage=256 GB").skus())
        .containsExactly("AT-1");
    assertThat(
            search
                .search("category=" + category + "&attr.storage=256 GB&attr.storage=512 GB")
                .skus())
        .containsExactlyInAnyOrder("AT-1", "AT-2", "AT-3");
    assertThat(
            search.search("category=" + category + "&attr.brand=Google&attr.storage=512 GB").skus())
        .containsExactly("AT-3");
  }

  @Test
  void aNumericRangeIsInclusiveAndEitherEndMayBeOpen() {
    var category = newCategory();
    product("NR-1").category(category).attribute("weight", "1.2").price(1000, "EUR").publish();
    product("NR-2").category(category).attribute("weight", "2").price(1000, "EUR").publish();
    product("NR-3").category(category).attribute("weight", "3.5").price(1000, "EUR").publish();
    product("NR-4").category(category).attribute("weight", "heavy").price(1000, "EUR").publish();
    search.awaitSearch("category=" + category, r -> r.total() == 4);

    assertThat(search.search("category=" + category + "&range.weight=1.2..2.0").skus())
        .containsExactlyInAnyOrder("NR-1", "NR-2");
    assertThat(search.search("category=" + category + "&range.weight=2..").skus())
        .containsExactlyInAnyOrder("NR-2", "NR-3");
    assertThat(search.search("category=" + category + "&range.weight=..1.5").skus())
        .containsExactly("NR-1");
  }

  @Test
  void aNumericRangeMatchesAnyVariantsAxisValue() {
    var category = newCategory();
    product("NA-1")
        .category(category)
        .variant(variant("NA-1-A", 1000, "EUR").axis("size", "13"))
        .variant(variant("NA-1-B", 1000, "EUR").axis("size", "15"))
        .publish();
    product("NA-2")
        .category(category)
        .variant(variant("NA-2", 1000, "EUR").axis("size", "11"))
        .publish();
    search.awaitSearch("category=" + category, r -> r.total() == 2);

    assertThat(search.search("category=" + category + "&range.size=14..16").skus())
        .containsExactly("NA-1");
  }

  @Test
  void aPriceRangeMatchesAnyVariantsPriceInItsCurrency() {
    var category = newCategory();
    product("PR-1")
        .category(category)
        .variant(variant("PR-1-A", 50000, "EUR"))
        .variant(variant("PR-1-B", 90000, "EUR"))
        .publish();
    product("PR-2").category(category).price(70000, "EUR").publish();
    product("PR-3").category(category).price(80000, "USD").publish();
    search.awaitSearch("category=" + category, r -> r.total() == 3);

    assertThat(search.search("category=" + category + "&currency=EUR&price=85000..95000").skus())
        .containsExactly("PR-1");
    assertThat(search.search("category=" + category + "&currency=EUR&price=60000..80000").skus())
        .containsExactly("PR-2");
    assertThat(search.search("category=" + category + "&currency=USD").skus())
        .containsExactly("PR-3");
  }

  @Test
  void inStockOnlyKeepsProductsWithAVariantInStock() {
    var category = newCategory();
    product("SO-1").category(category).price(1000, "EUR").publish();
    product("SO-2").category(category).price(1000, "EUR").publish();
    product("SO-3").category(category).price(1000, "EUR").publish();
    stock("SO-1", 1, 2);
    stock("SO-2", 1, 0);
    search.awaitSearch("category=" + category, r -> r.total() == 3 && r.facets().inStock() == 1);

    assertThat(search.search("category=" + category + "&inStock=true").skus())
        .containsExactly("SO-1");
  }

  @Test
  void eachSortOrdersTheResults() {
    var category = newCategory();
    var word = newWord();
    product("SR-OLD").category(category).name(word + " " + word).price(3000, "EUR").publish();
    product("SR-MID").category(category).name("Mid").description(word).price(1000, "EUR").publish();
    product("SR-NEW")
        .category(category)
        .name("New")
        .variant(variant("SR-NEW-A", 2000, "EUR"))
        .variant(variant("SR-NEW-B", 5000, "EUR"))
        .publish();
    search.awaitSearch("category=" + category, r -> r.total() == 3);

    var all = "category=" + category;
    assertThat(search.search(all + "&sort=newest").skus())
        .containsExactly("SR-NEW", "SR-MID", "SR-OLD");
    assertThat(search.search(all).skus())
        .as("relevance without text is newest")
        .containsExactly("SR-NEW", "SR-MID", "SR-OLD");
    assertThat(search.search(all + "&sort=price-asc").skus())
        .containsExactly("SR-MID", "SR-NEW", "SR-OLD");
    assertThat(search.search(all + "&sort=price-desc").skus())
        .containsExactly("SR-OLD", "SR-NEW", "SR-MID");
    assertThat(search.search(all + "&q=" + word + "&sort=relevance").skus())
        .containsExactly("SR-OLD", "SR-MID");
  }

  @Test
  void aPriceSortKeepsEachCurrencysProductsTogether() {
    var category = newCategory();
    product("CU-EUR-1").category(category).price(100, "EUR").publish();
    product("CU-USD-1").category(category).price(50, "USD").publish();
    product("CU-EUR-2").category(category).price(300, "EUR").publish();
    product("CU-USD-2").category(category).price(200, "USD").publish();
    search.awaitSearch("category=" + category, r -> r.total() == 4);

    assertThat(search.search("category=" + category + "&sort=price-asc").skus())
        .containsExactly("CU-EUR-1", "CU-EUR-2", "CU-USD-1", "CU-USD-2");
    assertThat(search.search("category=" + category + "&sort=price-desc").skus())
        .containsExactly("CU-EUR-2", "CU-EUR-1", "CU-USD-2", "CU-USD-1");
  }

  @Test
  void resultsComeAPageAtATimeWithTheTotal() {
    var category = newCategory();
    for (var i = 1; i <= 5; i++) {
      product("PG-" + i).category(category).price(i * 100L, "EUR").publish();
    }
    search.awaitSearch("category=" + category, r -> r.total() == 5);

    var sorted = "category=" + category + "&sort=price-asc&size=2";
    var second = search.search(sorted + "&page=1");
    assertThat(second.skus()).containsExactly("PG-3", "PG-4");
    assertThat(second.total()).isEqualTo(5);
    assertThat(second.page()).isEqualTo(1);
    assertThat(second.size()).isEqualTo(2);
    assertThat(search.search(sorted + "&page=2").skus()).containsExactly("PG-5");
    assertThat(search.search(sorted + "&page=3").skus()).isEmpty();
    assertThat(search.search("category=" + category).size()).isEqualTo(24);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sort=cheapest",
        "page=-1",
        "size=0",
        "size=101",
        "page=two",
        "price=100..200",
        "currency=EURO",
        "currency=EUR&price=200..100",
        "currency=EUR&price=1.5..",
        "currency=EUR&price=100",
        "range.weight=heavy..",
        "range.weight=3..1"
      })
  void aSearchThatCantMatchAsWrittenIsABadRequest(String query) {
    http.get()
        .uri("/search?" + query)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }
}
