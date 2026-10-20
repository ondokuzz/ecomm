package com.ecomm.returnswarranty.adapter.in.kafka;

import com.ecomm.returnswarranty.application.port.in.ProjectionUseCase;
import com.ecomm.returnswarranty.domain.DeliveredLine;
import com.ecomm.returnswarranty.domain.Order;
import com.ecomm.returnswarranty.domain.OrderLine;
import com.ecomm.returnswarranty.domain.Product;
import com.ecomm.returnswarranty.domain.SerialTracking;
import com.ecomm.returnswarranty.domain.Shipment;
import com.ecomm.returnswarranty.domain.WarrantyTerms;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

/**
 * Projects Fulfillment & Shipping's Shipments, for what was delivered to whom, from which Warehouse
 * and when; Catalog's Products, for each Variant's Warranty terms and Serial tracking; and Order
 * Management's Orders, for Refund amounts. Each event declares only the fields Returns & Warranty
 * reads; one missing a required field is dead-lettered at once.
 *
 * <p>While any Shipment kept before Warehouses were is without one, the Shipment topic is read from
 * the start whenever its partitions are assigned, so a stack upgraded from Sprint 5 fills them in
 * without a rebuild.
 */
@Component
class ProjectionListener implements ConsumerSeekAware {

  private static final String SHIPMENT_TOPIC = "fulfillment-shipping.shipment";

  private static final Logger log = LoggerFactory.getLogger(ProjectionListener.class);

  private static final String REPLACEMENT = "REPLACEMENT";

  /**
   * The fields of a {@code fulfillment-shipping.shipment} event Returns & Warranty needs. The
   * status is read as text, so a new one doesn't break the projection. A missing kind, from before
   * replacements, is an Order's Shipment.
   */
  record ShipmentEvent(
      @JsonProperty(required = true) UUID shipmentId,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) ShipmentState shipment) {

    record ShipmentState(
        String kind,
        @JsonProperty(required = true) String orderId,
        @JsonProperty(required = true) String customerId,
        @JsonProperty(required = true) String warehouseId,
        @JsonProperty(required = true) String status,
        Instant deliveredAt,
        @JsonProperty(required = true) List<Line> lines) {}

    record Line(
        @JsonProperty(required = true) String variantId,
        @JsonProperty(required = true) String productName,
        @JsonProperty(required = true) int quantity,
        @JsonProperty(required = true) String serialTracking,
        @JsonProperty(required = true) List<String> serialNumbers) {}
  }

  /**
   * The fields of a {@code catalog.product} event Returns & Warranty needs. Warranty terms and
   * Serial tracking are missing from events published before they existed, which read as none and
   * {@code NONE}.
   */
  record ProductEvent(
      @JsonProperty(required = true) String sku,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) ProductState product) {

    record ProductState(
        String serialTracking,
        Warranty warranty,
        @JsonProperty(required = true) List<Variant> variants) {}

    record Warranty(
        @JsonProperty(required = true) int months,
        @JsonProperty(required = true) String manufacturer) {}

    record Variant(@JsonProperty(required = true) String variantId) {}
  }

  /**
   * The fields of an {@code order-management.order} event Returns & Warranty needs: each line's
   * Price, and the Discounts and Tax, all in the Order's Currency. Discounts and Tax missing from
   * an event published before they existed read as none.
   */
  record OrderEvent(
      @JsonProperty(required = true) String orderId,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) OrderState order) {

    record OrderState(
        @JsonProperty(required = true) List<Line> lines, List<Discount> discounts, Money tax) {}

    record Line(
        @JsonProperty(required = true) String variantId,
        @JsonProperty(required = true) int quantity,
        @JsonProperty(required = true) Money unitPrice) {}

    record Discount(@JsonProperty(required = true) Money amount) {}

    record Money(
        @JsonProperty(required = true) long amountMinor,
        @JsonProperty(required = true) String currency) {}
  }

  private final ProjectionUseCase projection;

  ProjectionListener(ProjectionUseCase projection) {
    this.projection = projection;
  }

  @KafkaListener(topics = SHIPMENT_TOPIC, groupId = "returns-warranty.shipments")
  void on(ShipmentEvent event) {
    var shipment = event.shipment();
    // RMAs are on an Order's units; RMAs on replacement units are out of scope.
    if (REPLACEMENT.equals(shipment.kind())) {
      log.info("Skipped replacement Shipment {} version {}", event.shipmentId(), event.version());
      return;
    }
    var applied =
        projection.apply(
            new Shipment(
                event.shipmentId(),
                event.version(),
                shipment.orderId(),
                shipment.customerId(),
                shipment.warehouseId(),
                shipment.status(),
                shipment.deliveredAt(),
                shipment.lines().stream()
                    .map(
                        line ->
                            new DeliveredLine(
                                line.variantId(),
                                line.productName(),
                                line.quantity(),
                                SerialTracking.of(line.serialTracking()),
                                line.serialNumbers()))
                    .toList()));
    log.info(
        "{} Shipment {} version {}",
        applied ? "Applied" : "Ignored",
        event.shipmentId(),
        event.version());
  }

  @KafkaListener(topics = "catalog.product", groupId = "returns-warranty.products")
  void on(ProductEvent event) {
    var product = event.product();
    var warranty = product.warranty();
    var applied =
        projection.apply(
            new Product(
                event.sku(),
                event.version(),
                SerialTracking.of(product.serialTracking()),
                warranty == null
                    ? null
                    : new WarrantyTerms(warranty.months(), warranty.manufacturer()),
                product.variants().stream()
                    .map(ProductEvent.Variant::variantId)
                    .collect(Collectors.toSet())));
    log.info(
        "{} Product {} version {}", applied ? "Applied" : "Ignored", event.sku(), event.version());
  }

  @KafkaListener(topics = "order-management.order", groupId = "returns-warranty.orders")
  void on(OrderEvent event) {
    var order = event.order();
    var applied =
        projection.apply(
            new Order(
                event.orderId(),
                event.version(),
                order.lines().getFirst().unitPrice().currency(),
                order.lines().stream()
                    .map(
                        line ->
                            new OrderLine(
                                line.variantId(), line.quantity(), line.unitPrice().amountMinor()))
                    .toList(),
                order.discounts() == null
                    ? 0
                    : order.discounts().stream().mapToLong(d -> d.amount().amountMinor()).sum(),
                order.tax() == null ? 0 : order.tax().amountMinor()));
    log.info(
        "{} Order {} version {}",
        applied ? "Applied" : "Ignored",
        event.orderId(),
        event.version());
  }

  @Override
  public void onPartitionsAssigned(
      Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
    var shipmentPartitions =
        assignments.keySet().stream().filter(p -> p.topic().equals(SHIPMENT_TOPIC)).toList();
    if (!shipmentPartitions.isEmpty() && projection.shipmentsNeedReplay()) {
      log.info("Replaying {} from the start: Shipments without a Warehouse", SHIPMENT_TOPIC);
      callback.seekToBeginning(shipmentPartitions);
    }
  }
}
