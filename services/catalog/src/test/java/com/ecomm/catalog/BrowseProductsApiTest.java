package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;

/** What a Customer sees without logging in: the seeded Catalog. */
class BrowseProductsApiTest extends CatalogApiTest {

  /** The parts of a Product and a category a client reads, independent of the service's classes. */
  record ProductView(String sku, String name, String category) {}

  record CategoryView(String category, long productCount) {}

  @Test
  void anyoneCanViewASeededProductsDetail() {
    http.get()
        .uri("/products/{sku}", "PHN-PIXEL-9")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.sku")
        .isEqualTo("PHN-PIXEL-9")
        .jsonPath("$.name")
        .isEqualTo("Google Pixel 9")
        .jsonPath("$.category")
        .isEqualTo("phones")
        .jsonPath("$.price.amountMinor")
        .isEqualTo(79900)
        .jsonPath("$.price.currency")
        .isEqualTo("EUR")
        .jsonPath("$.attributes.storage")
        .isEqualTo("128 GB")
        .jsonPath("$.images[0]")
        .exists()
        .jsonPath("$.variants.length()")
        .isEqualTo(1)
        .jsonPath("$.variants[0].id")
        .isEqualTo("PHN-PIXEL-9")
        .jsonPath("$.variants[0].price.amountMinor")
        .isEqualTo(79900);
  }

  @Test
  void aMissingProductIsNotFound() {
    http.get()
        .uri("/products/{sku}", "NO-SUCH-SKU")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(404);
  }

  @Test
  void anyoneCanBrowseTheProductsInACategoryByName() {
    var audio = products("/products?category=audio");

    assertThat(audio).extracting(ProductView::category).containsOnly("audio");
    assertThat(audio)
        .extracting(ProductView::sku)
        .contains("AUD-SONY-WH1000XM5", "AUD-JBL-FLIP-6")
        .doesNotContain("PHN-PIXEL-9");
    assertThat(audio).extracting(ProductView::name).isSorted();
  }

  @Test
  void withoutACategoryEveryProductIsListed() {
    var all = products("/products");

    assertThat(all)
        .extracting(ProductView::sku)
        .contains("PHN-IPHONE-16", "LPT-XPS-13", "AUD-JBL-FLIP-6");
    assertThat(all).extracting(ProductView::name).isSorted();
  }

  @Test
  void anUnknownCategoryHasNoProducts() {
    assertThat(products("/products?category=garden-furniture")).isEmpty();
  }

  @Test
  void anyoneCanListTheCategories() {
    var categories =
        http.get()
            .uri("/categories")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<CategoryView>>() {})
            .returnResult()
            .getResponseBody();

    assertThat(categories)
        .extracting(CategoryView::category)
        .containsSubsequence("audio", "laptops", "phones");
    assertThat(categories).allSatisfy(c -> assertThat(c.productCount()).isPositive());
  }

  private List<ProductView> products(String uri) {
    return http.get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<List<ProductView>>() {})
        .returnResult()
        .getResponseBody();
  }
}
