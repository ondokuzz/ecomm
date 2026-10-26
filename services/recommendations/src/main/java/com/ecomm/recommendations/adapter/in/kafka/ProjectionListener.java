package com.ecomm.recommendations.adapter.in.kafka;

import com.ecomm.recommendations.application.port.in.ProjectionUseCase;
import com.ecomm.recommendations.application.port.in.ProjectionUseCase.CategoryPublished;
import com.ecomm.recommendations.application.port.in.ProjectionUseCase.OrderPublished;
import com.ecomm.recommendations.application.port.in.ProjectionUseCase.ProductPublished;
import com.ecomm.recommendations.application.port.in.ProjectionUseCase.StockPublished;
import com.ecomm.recommendations.domain.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

/**
 * Projects Order Management's Orders, Catalog's Products and Categories, and Inventory's Stock into
 * Recommendations, one consumer group per topic. Rebuilding the projection resets these groups to
 * the earliest offset (README).
 *
 * <p>While any Product kept before Sprint 6 is without a Category, the Product topic is read from
 * the start whenever its partitions are assigned, so a stack upgraded from Sprint 5 fills them in
 * without a rebuild.
 */
@Component
class ProjectionListener implements ConsumerSeekAware {

  private static final String PRODUCT_TOPIC = "catalog.product";

  private static final Logger log = LoggerFactory.getLogger(ProjectionListener.class);

  /**
   * The fields of an {@code order-management.order} event Recommendations needs. The Status is read
   * as text, since Order Management may add one (event-schemas README). An event missing a field
   * Recommendations can't do without can't be read, and is dead-lettered at once.
   */
  record OrderEvent(
      @JsonProperty(required = true) String orderId,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) Order order) {
    record Order(
        @JsonProperty(required = true) String status,
        @JsonProperty(required = true) Instant placedAt,
        @JsonProperty(required = true) List<Line> lines) {}

    record Line(@JsonProperty(required = true) String variantId) {}
  }

  /**
   * The fields of a {@code catalog.product} event Recommendations needs: whether it was removed,
   * its Category's slug, and its Variants' IDs.
   */
  record ProductEvent(
      @JsonProperty(required = true) String sku,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) Product product) {
    record Product(
        @JsonProperty(required = true) boolean removed,
        @JsonProperty(required = true) String category,
        @JsonProperty(required = true) List<Variant> variants) {}

    record Variant(@JsonProperty(required = true) String variantId) {}
  }

  /** The fields of a {@code catalog.category} event Recommendations needs. */
  record CategoryEvent(
      @JsonProperty(required = true) String slug,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) Category category) {
    record Category(
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) boolean removed) {}
  }

  /** The fields of an {@code inventory.stock} event Recommendations needs. */
  record StockEvent(
      @JsonProperty(required = true) String variantId,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) Stock stock) {
    record Stock(
        @JsonProperty(required = true) int available,
        @JsonProperty(required = true) boolean stocked) {}
  }

  private final ProjectionUseCase projection;

  ProjectionListener(ProjectionUseCase projection) {
    this.projection = projection;
  }

  @KafkaListener(topics = "order-management.order", groupId = "recommendations.orders")
  void on(OrderEvent event) {
    var order = event.order();
    var applied =
        projection.apply(
            new OrderPublished(
                event.orderId(),
                event.version(),
                OrderStatus.of(order.status()),
                order.placedAt(),
                order.lines().stream().map(OrderEvent.Line::variantId).toList()));
    logOutcome(applied, "Order", event.orderId(), event.version());
  }

  @KafkaListener(topics = PRODUCT_TOPIC, groupId = "recommendations.products")
  void on(ProductEvent event) {
    var product = event.product();
    var applied =
        projection.apply(
            new ProductPublished(
                event.sku(),
                event.version(),
                product.removed(),
                product.category(),
                product.variants().stream().map(ProductEvent.Variant::variantId).toList()));
    logOutcome(applied, "Product", event.sku(), event.version());
  }

  @KafkaListener(topics = "catalog.category", groupId = "recommendations.categories")
  void on(CategoryEvent event) {
    var category = event.category();
    var applied =
        projection.apply(
            new CategoryPublished(
                event.slug(), event.version(), category.name(), category.removed()));
    logOutcome(applied, "Category", event.slug(), event.version());
  }

  @KafkaListener(topics = "inventory.stock", groupId = "recommendations.stock")
  void on(StockEvent event) {
    var stock = event.stock();
    var applied =
        projection.apply(
            new StockPublished(
                event.variantId(), event.version(), stock.stocked(), stock.available()));
    logOutcome(applied, "Stock of", event.variantId(), event.version());
  }

  @Override
  public void onPartitionsAssigned(
      Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
    var productPartitions =
        assignments.keySet().stream().filter(p -> p.topic().equals(PRODUCT_TOPIC)).toList();
    if (!productPartitions.isEmpty() && projection.productsNeedReplay()) {
      log.info("Replaying {} from the start: Products without a Category", PRODUCT_TOPIC);
      callback.seekToBeginning(productPartitions);
    }
  }

  private static void logOutcome(boolean applied, String what, String id, long version) {
    log.info("{} {} {} version {}", applied ? "Applied" : "Ignored", what, id, version);
  }
}
