package com.ecomm.reviewsratings.adapter.in.kafka;

import com.ecomm.reviewsratings.application.port.in.ProjectionUseCase;
import com.ecomm.reviewsratings.application.port.in.ProjectionUseCase.OrderPublished;
import com.ecomm.reviewsratings.application.port.in.ProjectionUseCase.ProductPublished;
import com.ecomm.reviewsratings.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Projects Order Management's Orders and Catalog's Products into Reviews, one consumer group per
 * topic. Rebuilding the projection resets these groups to the earliest offset (README).
 */
@Component
class ProjectionListener {

  private static final Logger log = LoggerFactory.getLogger(ProjectionListener.class);

  /**
   * The fields of an {@code order-management.order} event Reviews needs. The Status is read as
   * text, since Order Management may add one (event-schemas README).
   */
  record OrderEvent(String orderId, long version, Order order) {
    record Order(String customerId, String status, Instant placedAt, List<Line> lines) {}

    record Line(String variantId) {}
  }

  /** The fields of a {@code catalog.product} event Reviews needs: its Variants' IDs. */
  record ProductEvent(String sku, long version, Product product) {
    record Product(List<Variant> variants) {}

    record Variant(String variantId) {}
  }

  private final ProjectionUseCase projection;

  ProjectionListener(ProjectionUseCase projection) {
    this.projection = projection;
  }

  @KafkaListener(topics = "order-management.order", groupId = "reviews-ratings.orders")
  void on(OrderEvent event) {
    var order = event.order();
    var applied =
        projection.apply(
            new OrderPublished(
                event.orderId(),
                event.version(),
                order.customerId(),
                OrderStatus.of(order.status()),
                order.placedAt(),
                order.lines().stream().map(OrderEvent.Line::variantId).toList()));
    logOutcome(applied, "Order", event.orderId(), event.version());
  }

  @KafkaListener(topics = "catalog.product", groupId = "reviews-ratings.products")
  void on(ProductEvent event) {
    var applied =
        projection.apply(
            new ProductPublished(
                event.sku(),
                event.version(),
                event.product().variants().stream().map(ProductEvent.Variant::variantId).toList()));
    logOutcome(applied, "Product", event.sku(), event.version());
  }

  private static void logOutcome(boolean applied, String what, String id, long version) {
    log.info("{} {} {} version {}", applied ? "Applied" : "Ignored", what, id, version);
  }
}
