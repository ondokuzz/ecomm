package com.ecomm.searchdiscovery;

import static com.ecomm.searchdiscovery.Events.category;
import static com.ecomm.searchdiscovery.Events.product;
import static com.ecomm.searchdiscovery.Events.stock;
import static com.ecomm.searchdiscovery.Events.variant;
import static com.ecomm.searchdiscovery.SearchClient.newCategory;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.searchdiscovery.SearchClient.CategoryCount;
import com.ecomm.searchdiscovery.SearchClient.PriceRange;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Every search answers with facets, each counted as if its own filter weren't applied. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class FacetsApiTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;
  SearchClient search;
  String phones;

  /**
   * Three phones: a Google in 128 and 256 GB, in Stock; a Google in 512 GB; an Apple in 128 GB, in
   * Stock. Their Category defines brand, storage (an axis) and screen (a NUMBER).
   */
  @BeforeEach
  void setUp() {
    search = new SearchClient(http);
    phones = newCategory();
    category(phones)
        .name("Phones")
        .attribute("brand", "TEXT", false)
        .attribute("storage", "ENUM", true, "128 GB", "256 GB", "512 GB", "1 TB")
        .attribute("screen", "NUMBER", false)
        .publish();
    product(phones + "-1")
        .category(phones)
        .attribute("brand", "Google")
        .attribute("screen", "6.3")
        .variant(variant(phones + "-1-128", 69900, "EUR").axis("storage", "128 GB"))
        .variant(variant(phones + "-1-256", 79900, "EUR").axis("storage", "256 GB"))
        .publish();
    product(phones + "-2")
        .category(phones)
        .attribute("brand", "Google")
        .attribute("screen", "6.8")
        .variant(variant(phones + "-2", 99900, "EUR").axis("storage", "512 GB"))
        .publish();
    product(phones + "-3")
        .category(phones)
        .attribute("brand", "Apple")
        .attribute("screen", "6.1")
        .variant(variant(phones + "-3", 89900, "EUR").axis("storage", "128 GB"))
        .publish();
    stock(phones + "-1-256", 1, 4);
    stock(phones + "-3", 1, 1);
    search.awaitSearch(
        "category=" + phones,
        r -> r.total() == 3 && r.facets().inStock() == 2 && !r.facets().attributes().isEmpty());
  }

  @Test
  void choosingAValueStillShowsItsSiblingsCounts() {
    var results = search.search("category=" + phones + "&attr.storage=128 GB");

    assertThat(results.total()).isEqualTo(2);
    assertThat(results.valueCounts("storage"))
        .containsExactly(
            Map.entry("128 GB", 2L),
            Map.entry("256 GB", 1L),
            Map.entry("512 GB", 1L),
            Map.entry("1 TB", 0L));
    assertThat(results.valueCounts("brand"))
        .as("other attributes are counted within the chosen storage")
        .containsExactly(Map.entry("Apple", 1L), Map.entry("Google", 1L));
  }

  @Test
  void theCategoryFacetIsCountedWithoutTheCategoryAndAttributeFacetsComeWithOne() {
    var all = search.search("attr.brand=Apple");

    assertThat(all.facets().categories()).contains(new CategoryCount(phones, "Phones", 1));
    assertThat(all.facets().attributes()).isEmpty();
    assertThat(search.search("category=" + phones).facets().attributes())
        .extracting(SearchClient.AttributeFacet::name)
        .containsExactly("brand", "storage", "screen");
  }

  @Test
  void aNumberFacetIsItsLowestAndHighestValue() {
    var screen =
        search
            .search("category=" + phones + "&range.screen=6.2..&attr.brand=Google")
            .attribute("screen");

    assertThat(screen.type()).isEqualTo("NUMBER");
    assertThat(screen.min()).isEqualByComparingTo("6.3");
    assertThat(screen.max()).isEqualByComparingTo("6.8");
  }

  @Test
  void thePriceAndInStockFacetsAreCountedWithoutTheirOwnFilters() {
    var results =
        search.search("category=" + phones + "&currency=EUR&price=95000..100000&inStock=true");

    assertThat(results.total()).isZero();
    assertThat(results.facets().prices()).containsExactly(new PriceRange("EUR", 69900, 89900));
    assertThat(results.facets().inStock()).isZero();
    assertThat(search.search("category=" + phones + "&inStock=true").facets().inStock())
        .isEqualTo(2);
  }
}
