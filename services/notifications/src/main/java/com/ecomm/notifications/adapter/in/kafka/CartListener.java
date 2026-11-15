package com.ecomm.notifications.adapter.in.kafka;

import com.ecomm.notifications.application.port.in.CartEventsUseCase;
import com.ecomm.notifications.application.port.in.CartEventsUseCase.CartAbandonedEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Cart's Abandoned Carts, which may get a Cart reminder. */
@Component
class CartListener {

  private static final Logger log = LoggerFactory.getLogger(CartListener.class);

  /** The fields of a {@code cart.cart} event Notifications needs. */
  record CartEvent(
      @JsonProperty(required = true) String eventId,
      @JsonProperty(required = true) String customerId,
      @JsonProperty(required = true) String change,
      @JsonProperty(required = true) Cart cart) {
    record Cart(@JsonProperty(required = true) Instant lastChangedAt) {}
  }

  private final CartEventsUseCase carts;

  CartListener(CartEventsUseCase carts) {
    this.carts = carts;
  }

  /**
   * On its first start the group reads from the latest offset, so no Cart abandoned before cart
   * reminders existed gets one; from then on it resumes where it left off.
   */
  @KafkaListener(
      topics = "cart.cart",
      groupId = "notifications.carts",
      properties = "auto.offset.reset=latest")
  void on(CartEvent event) {
    if (!"ABANDONED".equals(event.change())) {
      return;
    }
    var recorded =
        carts.cartAbandoned(
            new CartAbandonedEvent(
                event.eventId(), event.customerId(), event.cart().lastChangedAt()));
    if (!recorded.isEmpty()) {
      log.info("Recorded {} from event {}", recorded, event.eventId());
    }
  }
}
