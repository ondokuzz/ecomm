package com.ecomm.notifications.adapter.in.kafka;

import com.ecomm.notifications.application.port.in.ProductNamesUseCase;
import com.ecomm.notifications.application.port.in.ProductNamesUseCase.ProductPublished;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Projects Catalog's Products' names, which name an Order's lines in its messages. Its group reads
 * from the start when new; {@code make notifications-rebuild} resets it to the earliest offset.
 */
@Component
class ProductListener {

  private static final Logger log = LoggerFactory.getLogger(ProductListener.class);

  /** The fields of a {@code catalog.product} event Notifications needs. */
  record ProductEvent(
      @JsonProperty(required = true) String sku,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) Product product) {
    record Product(
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) List<Variant> variants) {}

    record Variant(
        @JsonProperty(required = true) String variantId,
        @JsonProperty(required = true) LinkedHashMap<String, String> axisValues) {}
  }

  private final ProductNamesUseCase products;

  ProductListener(ProductNamesUseCase products) {
    this.products = products;
  }

  @KafkaListener(topics = "catalog.product", groupId = "notifications.products")
  void on(ProductEvent event) {
    var variants = new LinkedHashMap<String, Map<String, String>>();
    for (var variant : event.product().variants()) {
      variants.put(variant.variantId(), variant.axisValues());
    }
    var applied =
        products.apply(
            new ProductPublished(event.sku(), event.version(), event.product().name(), variants));
    log.info(
        "{} Product {} version {}", applied ? "Applied" : "Ignored", event.sku(), event.version());
  }
}
