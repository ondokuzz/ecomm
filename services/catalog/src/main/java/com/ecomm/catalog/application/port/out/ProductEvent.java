package com.ecomm.catalog.application.port.out;

import com.ecomm.catalog.domain.Product;
import com.ecomm.catalog.domain.Variant;
import com.ecomm.commons.events.IntegrationEvent;
import java.util.List;
import java.util.Map;

/**
 * The {@code catalog.product} event: a snapshot of a whole Product, published whenever it is
 * created, changed or removed, and once for every Product stored before Product events. A removed
 * Product's last event is its last state, marked {@code removed}. Its shape is defined by {@code
 * platform/event-schemas/schemas/catalog.product.json}.
 */
public record ProductEvent(String sku, long version, Change change, Snapshot product)
    implements IntegrationEvent {

  public static final String TOPIC = "catalog.product";

  /** Why the event was published. */
  public enum Change {
    CREATED,
    UPDATED,
    REMOVED,
    BACKFILLED
  }

  /** {@code description} is null, and left out of the event, when the Product has none. */
  public record Snapshot(
      String name,
      String description,
      String category,
      Map<String, String> attributes,
      List<String> images,
      List<VariantSnapshot> variants,
      boolean removed) {}

  public record VariantSnapshot(
      String variantId, Map<String, String> axisValues, Price price, List<String> images) {}

  public record Price(long amountMinor, String currency) {}

  public static ProductEvent of(Product product, long version, Change change) {
    return new ProductEvent(
        product.sku(),
        version,
        change,
        new Snapshot(
            product.name(),
            product.description(),
            product.category(),
            product.attributes(),
            product.images(),
            product.variants().stream().map(ProductEvent::snapshotOf).toList(),
            change == Change.REMOVED));
  }

  private static VariantSnapshot snapshotOf(Variant variant) {
    return new VariantSnapshot(
        variant.id(),
        variant.axisValues(),
        new Price(variant.price().amountMinor(), variant.price().currency().getCurrencyCode()),
        variant.images());
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public String aggregateId() {
    return sku;
  }
}
