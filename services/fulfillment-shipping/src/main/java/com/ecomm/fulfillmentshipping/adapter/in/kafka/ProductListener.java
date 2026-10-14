package com.ecomm.fulfillmentshipping.adapter.in.kafka;

import com.ecomm.fulfillmentshipping.application.port.in.ProjectProductUseCase;
import com.ecomm.fulfillmentshipping.domain.Product;
import com.ecomm.fulfillmentshipping.domain.SerialTracking;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Projects Catalog's Products into Fulfillment & Shipping, for the Product name and Serial tracking
 * of each Variant a Shipment carries.
 */
@Component
class ProductListener {

  private static final Logger log = LoggerFactory.getLogger(ProductListener.class);

  /**
   * The fields of a {@code catalog.product} event Fulfillment & Shipping needs. Serial tracking is
   * read as text and is missing from events published before it existed, which read as {@code
   * NONE}. An event missing a required field is dead-lettered at once.
   */
  record ProductEvent(
      @JsonProperty(required = true) String sku,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) ProductState product) {

    record ProductState(
        @JsonProperty(required = true) String name,
        String serialTracking,
        @JsonProperty(required = true) List<Variant> variants) {}

    record Variant(@JsonProperty(required = true) String variantId) {}
  }

  private final ProjectProductUseCase projection;

  ProductListener(ProjectProductUseCase projection) {
    this.projection = projection;
  }

  @KafkaListener(topics = "catalog.product", groupId = "fulfillment-shipping.products")
  void on(ProductEvent event) {
    var product = event.product();
    var applied =
        projection.apply(
            new Product(
                event.sku(),
                event.version(),
                product.name(),
                SerialTracking.of(product.serialTracking()),
                product.variants().stream()
                    .map(ProductEvent.Variant::variantId)
                    .collect(Collectors.toSet())));
    log.info(
        "{} Product {} version {}", applied ? "Applied" : "Ignored", event.sku(), event.version());
  }
}
