package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;

/** The seeded Categories define the attributes the seeded Products carry. */
class SeedCategoriesApiTest extends CatalogApiTest {

  record CategoryView(
      String slug, String name, long productCount, List<DefinitionView> attributes) {}

  record DefinitionView(
      String name, String type, List<String> values, boolean required, boolean variantAxis) {}

  /** Enough of a Product to write it back unchanged. */
  record ProductView(
      String sku,
      String name,
      String category,
      Map<String, String> attributes,
      Map<String, Object> price,
      List<String> images) {}

  @ParameterizedTest
  @CsvSource({
    "phones, Phones, 'brand,screen', 'color,storage'",
    "laptops, Laptops, 'brand,cpu,screen', 'ram,storage'",
    "audio, Audio, 'brand,type,wireless,noiseCancelling,waterproof', ''",
  })
  void eachSeedCategoryDefinesItsProductsAttributesAndVariantAxes(
      String slug, String name, String attributes, String axes) {
    var category =
        http.get()
            .uri("/categories/{slug}", slug)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CategoryView.class)
            .returnResult()
            .getResponseBody();

    assertThat(category.name()).isEqualTo(name);
    assertThat(category.productCount()).isPositive();
    assertThat(category.attributes())
        .filteredOn(d -> !d.variantAxis())
        .extracting(DefinitionView::name)
        .containsExactly(attributes.split(","));
    assertThat(category.attributes())
        .filteredOn(DefinitionView::variantAxis)
        .extracting(DefinitionView::name)
        .containsExactly(axes.isEmpty() ? new String[0] : axes.split(","));
  }

  @ParameterizedTest
  @ValueSource(strings = {"phones", "laptops", "audio"})
  void everySeedProductStillPassesValidation(String slug) {
    var products =
        http.get()
            .uri("/products?category={slug}", slug)
            .exchange()
            .expectBody(new ParameterizedTypeReference<List<ProductView>>() {})
            .returnResult()
            .getResponseBody();
    assertThat(products).isNotEmpty();

    // Writing each Product back unchanged runs it through its Category's definitions.
    for (var product : products) {
      http.put()
          .uri("/products/{sku}", product.sku())
          .headers(h -> h.setBearerAuth(staffToken()))
          .contentType(MediaType.APPLICATION_JSON)
          .body(product)
          .exchange()
          .expectStatus()
          .isOk();
    }
  }
}
