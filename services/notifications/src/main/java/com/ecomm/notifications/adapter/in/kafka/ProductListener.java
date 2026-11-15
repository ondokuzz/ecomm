package com.ecomm.notifications.adapter.in.kafka;

import com.ecomm.commons.money.Money;
import com.ecomm.notifications.application.port.in.ProductNamesUseCase;
import com.ecomm.notifications.application.port.in.ProductNamesUseCase.ProductPublished;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

/**
 * Projects Catalog's Products' names, which name an Order's lines in its messages, and their
 * Variants' Prices. Its group reads from the start when new; {@code make notifications-rebuild}
 * resets it to the earliest offset. While any Variant projected before Prices were kept has none,
 * the topic is read from the start whenever its partitions are assigned, so a stack upgraded from
 * Sprint 7 fills them in on {@code make up}, with no rebuild.
 */
@Component
class ProductListener implements ConsumerSeekAware {

  private static final String TOPIC = "catalog.product";

  private static final Logger log = LoggerFactory.getLogger(ProductListener.class);

  /** The fields of a {@code catalog.product} event Notifications needs. */
  record ProductEvent(
      @JsonProperty(required = true) Instant occurredAt,
      @JsonProperty(required = true) String sku,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) Product product) {
    record Product(
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) List<Variant> variants) {}

    record Variant(
        @JsonProperty(required = true) String variantId,
        @JsonProperty(required = true) LinkedHashMap<String, String> axisValues,
        @JsonProperty(required = true) Price price) {}

    record Price(
        @JsonProperty(required = true) long amountMinor,
        @JsonProperty(required = true) String currency) {}
  }

  private final ProductNamesUseCase products;

  ProductListener(ProductNamesUseCase products) {
    this.products = products;
  }

  @Override
  public void onPartitionsAssigned(
      Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
    var partitions = assignments.keySet().stream().filter(p -> p.topic().equals(TOPIC)).toList();
    if (!partitions.isEmpty() && products.needsReplay()) {
      log.info("Replaying {} from the start: Variants without a Price", TOPIC);
      callback.seekToBeginning(partitions);
    }
  }

  @KafkaListener(topics = TOPIC, groupId = "notifications.products")
  void on(ProductEvent event) {
    var variants = new LinkedHashMap<String, Map<String, String>>();
    var prices = new LinkedHashMap<String, Money>();
    for (var variant : event.product().variants()) {
      variants.put(variant.variantId(), variant.axisValues());
      prices.put(
          variant.variantId(), Money.of(variant.price().amountMinor(), variant.price().currency()));
    }
    var applied =
        products.apply(
            new ProductPublished(
                event.sku(),
                event.version(),
                event.occurredAt(),
                event.product().name(),
                variants,
                prices));
    log.info(
        "{} Product {} version {}", applied ? "Applied" : "Ignored", event.sku(), event.version());
  }
}
