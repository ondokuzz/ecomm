package com.ecomm.returnswarranty.adapter.in.kafka;

import com.ecomm.returnswarranty.application.port.in.ProjectionUseCase;
import com.ecomm.returnswarranty.domain.DeliveredLine;
import com.ecomm.returnswarranty.domain.Product;
import com.ecomm.returnswarranty.domain.SerialTracking;
import com.ecomm.returnswarranty.domain.Shipment;
import com.ecomm.returnswarranty.domain.WarrantyTerms;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Projects Fulfillment & Shipping's Shipments, for what was delivered to whom and when, and
 * Catalog's Products, for each Variant's Warranty terms and Serial tracking. Each event declares
 * only the fields Returns & Warranty reads; one missing a required field is dead-lettered at once.
 */
@Component
class ProjectionListener {

  private static final Logger log = LoggerFactory.getLogger(ProjectionListener.class);

  /**
   * The fields of a {@code fulfillment-shipping.shipment} event Returns & Warranty needs. The
   * status is read as text, so a new one doesn't break the projection.
   */
  record ShipmentEvent(
      @JsonProperty(required = true) UUID shipmentId,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) ShipmentState shipment) {

    record ShipmentState(
        @JsonProperty(required = true) String orderId,
        @JsonProperty(required = true) String customerId,
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

  private final ProjectionUseCase projection;

  ProjectionListener(ProjectionUseCase projection) {
    this.projection = projection;
  }

  @KafkaListener(topics = "fulfillment-shipping.shipment", groupId = "returns-warranty.shipments")
  void on(ShipmentEvent event) {
    var shipment = event.shipment();
    var applied =
        projection.apply(
            new Shipment(
                event.shipmentId(),
                event.version(),
                shipment.orderId(),
                shipment.customerId(),
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
}
