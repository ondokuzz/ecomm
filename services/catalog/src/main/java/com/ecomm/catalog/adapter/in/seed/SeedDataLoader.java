package com.ecomm.catalog.adapter.in.seed;

import com.ecomm.catalog.application.port.in.SeedCatalogUseCase;
import com.ecomm.catalog.domain.Product;
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

/** On startup, loads the seed Products into the Catalog if it is empty. */
@Component
class SeedDataLoader implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

  private final SeedCatalogUseCase seedCatalog;
  private final JsonMapper json;
  private final Resource seed;

  SeedDataLoader(
      SeedCatalogUseCase seedCatalog,
      JsonMapper json,
      @Value("${ecomm.catalog.seed}") Resource seed) {
    this.seedCatalog = seedCatalog;
    this.json = json;
    this.seed = seed;
  }

  @Override
  public void run(ApplicationArguments args) throws IOException {
    List<SeedProduct> products;
    try (InputStream in = seed.getInputStream()) {
      products = json.readValue(in, new TypeReference<>() {});
    }
    if (seedCatalog.seedIfEmpty(products.stream().map(SeedProduct::toProduct).toList())) {
      log.info("Seeded the Catalog with {} Products", products.size());
    } else {
      log.info("Catalog already holds Products; skipped seeding");
    }
  }

  record SeedProduct(
      String sku,
      String name,
      String category,
      Map<String, String> attributes,
      SeedPrice price,
      List<String> images) {

    Product toProduct() {
      return new Product(
          sku, name, category, attributes, Money.of(price.amountMinor, price.currency), images);
    }
  }

  record SeedPrice(long amountMinor, String currency) {}
}
