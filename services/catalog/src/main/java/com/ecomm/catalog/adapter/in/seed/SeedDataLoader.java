package com.ecomm.catalog.adapter.in.seed;

import com.ecomm.catalog.application.port.in.SeedCatalogUseCase;
import com.ecomm.catalog.domain.AttributeDefinition;
import com.ecomm.catalog.domain.AttributeType;
import com.ecomm.catalog.domain.Category;
import com.ecomm.catalog.domain.Product;
import com.ecomm.catalog.domain.SerialTracking;
import com.ecomm.catalog.domain.Variant;
import com.ecomm.catalog.domain.WarrantyTerms;
import com.ecomm.commons.money.Money;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * On startup, loads the seed Categories and Products into the Catalog if it is empty. Otherwise it
 * gives the seeded Products stored before Warranty terms the seed's Warranty terms and Serial
 * tracking.
 */
@Component
class SeedDataLoader implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

  private final SeedCatalogUseCase seedCatalog;
  private final JsonMapper json;
  private final Resource categories;
  private final Resource products;

  SeedDataLoader(
      SeedCatalogUseCase seedCatalog,
      JsonMapper json,
      @Value("${ecomm.catalog.seed.categories}") Resource categories,
      @Value("${ecomm.catalog.seed.products}") Resource products) {
    this.seedCatalog = seedCatalog;
    this.json = json;
    this.categories = categories;
    this.products = products;
  }

  @Override
  public void run(ApplicationArguments args) throws IOException {
    List<SeedCategory> categories = read(this.categories, new TypeReference<>() {});
    List<SeedProduct> products = read(this.products, new TypeReference<>() {});
    var seedProducts = products.stream().map(SeedProduct::toProduct).toList();
    if (seedCatalog.seedIfEmpty(
        categories.stream().map(SeedCategory::toCategory).toList(), seedProducts)) {
      log.info(
          "Seeded the Catalog with {} Categories and {} Products",
          categories.size(),
          products.size());
    } else {
      log.info("Catalog already holds data; skipped seeding");
      var filled = seedCatalog.fillInWarrantyTerms(seedProducts);
      if (filled > 0) {
        log.info("Gave {} seeded Products their Warranty terms and Serial tracking", filled);
      }
    }
  }

  private <T> T read(Resource resource, TypeReference<T> type) throws IOException {
    try (InputStream in = resource.getInputStream()) {
      return json.readValue(in, type);
    }
  }

  record SeedCategory(String slug, String name, List<SeedDefinition> attributes) {

    Category toCategory() {
      return new Category(
          slug, name, attributes.stream().map(SeedDefinition::toDefinition).toList());
    }
  }

  record SeedDefinition(
      String name, AttributeType type, List<String> values, boolean required, boolean variantAxis) {

    AttributeDefinition toDefinition() {
      return new AttributeDefinition(name, type, values, required, variantAxis);
    }
  }

  record SeedProduct(
      String sku,
      String name,
      String description,
      String category,
      Map<String, String> attributes,
      List<String> images,
      List<SeedVariant> variants,
      SeedWarranty warranty,
      SerialTracking serialTracking) {

    Product toProduct() {
      return new Product(
          sku,
          name,
          description,
          category,
          attributes,
          images,
          variants.stream().map(SeedVariant::toVariant).toList(),
          warranty == null ? null : new WarrantyTerms(warranty.months, warranty.manufacturer),
          serialTracking);
    }
  }

  record SeedWarranty(int months, String manufacturer) {}

  record SeedVariant(
      String id, Map<String, String> axisValues, SeedPrice price, List<String> images) {

    Variant toVariant() {
      return new Variant(id, axisValues, Money.of(price.amountMinor, price.currency), images);
    }
  }

  record SeedPrice(long amountMinor, String currency) {}
}
