package com.ecomm.searchdiscovery.adapter.in.kafka;

import com.ecomm.commons.money.Money;
import com.ecomm.searchdiscovery.application.port.in.ProjectCatalogUseCase;
import com.ecomm.searchdiscovery.application.port.in.ProjectCatalogUseCase.CategoryPublished;
import com.ecomm.searchdiscovery.application.port.in.ProjectCatalogUseCase.ProductPublished;
import com.ecomm.searchdiscovery.application.port.in.ProjectCatalogUseCase.StockPublished;
import com.ecomm.searchdiscovery.domain.AttributeDefinition;
import com.ecomm.searchdiscovery.domain.AttributeType;
import com.ecomm.searchdiscovery.domain.SearchableVariant;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Projects Catalog's Products and Categories and Inventory's Stock into Search, one consumer group
 * per topic. Rebuilding the projection resets these groups to the earliest offset (README).
 */
@Component
class CatalogListener {

  private static final Logger log = LoggerFactory.getLogger(CatalogListener.class);

  /** The fields of a {@code catalog.product} event Search needs; the others are ignored. */
  record ProductEvent(String sku, long version, Instant occurredAt, Product product) {
    record Product(
        String name,
        String description,
        String category,
        Map<String, String> attributes,
        List<String> images,
        List<Variant> variants,
        boolean removed) {}

    record Variant(
        String variantId, Map<String, String> axisValues, Price price, List<String> images) {}

    record Price(long amountMinor, String currency) {}
  }

  record CategoryEvent(String slug, long version, Category category) {
    record Category(String name, List<Attribute> attributes, boolean removed) {}

    record Attribute(String name, AttributeType type, List<String> values) {}
  }

  record StockEvent(String variantId, long version, Stock stock) {
    record Stock(long available, boolean stocked) {}
  }

  private final ProjectCatalogUseCase projection;

  CatalogListener(ProjectCatalogUseCase projection) {
    this.projection = projection;
  }

  @KafkaListener(topics = "catalog.product", groupId = "search-discovery.products")
  void on(ProductEvent event) {
    var product = event.product();
    var applied =
        projection.apply(
            new ProductPublished(
                event.sku(),
                event.version(),
                event.occurredAt(),
                product.name(),
                product.description(),
                product.category(),
                orEmpty(product.attributes()),
                orEmpty(product.images()),
                product.variants().stream()
                    .map(
                        v ->
                            new SearchableVariant(
                                v.variantId(),
                                orEmpty(v.axisValues()),
                                Money.of(v.price().amountMinor(), v.price().currency()),
                                orEmpty(v.images())))
                    .toList(),
                product.removed()));
    logOutcome(applied, "Product", event.sku(), event.version());
  }

  @KafkaListener(topics = "catalog.category", groupId = "search-discovery.categories")
  void on(CategoryEvent event) {
    var category = event.category();
    var applied =
        projection.apply(
            new CategoryPublished(
                event.slug(),
                event.version(),
                category.name(),
                orEmpty(category.attributes()).stream()
                    .map(a -> new AttributeDefinition(a.name(), a.type(), orEmpty(a.values())))
                    .toList(),
                category.removed()));
    logOutcome(applied, "Category", event.slug(), event.version());
  }

  @KafkaListener(topics = "inventory.stock", groupId = "search-discovery.stock")
  void on(StockEvent event) {
    var applied =
        projection.apply(
            new StockPublished(
                event.variantId(),
                event.version(),
                event.stock().available(),
                event.stock().stocked()));
    logOutcome(applied, "Stock of", event.variantId(), event.version());
  }

  private static void logOutcome(boolean applied, String what, String id, long version) {
    log.info("{} {} {} version {}", applied ? "Applied" : "Ignored", what, id, version);
  }

  private static <T> List<T> orEmpty(List<T> list) {
    return list == null ? List.of() : list;
  }

  private static Map<String, String> orEmpty(Map<String, String> map) {
    return map == null ? Map.of() : map;
  }
}
