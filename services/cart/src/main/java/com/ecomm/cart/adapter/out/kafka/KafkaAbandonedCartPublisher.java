package com.ecomm.cart.adapter.out.kafka;

import com.ecomm.cart.application.port.out.AbandonedCartPublisher;
import com.ecomm.cart.application.port.out.TimeSource;
import com.ecomm.cart.domain.AbandonedCart;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;

/**
 * Sends each abandonment to {@code cart.cart}, keyed by the Customer, with Kafka's idempotent
 * producer, and waits for the acknowledgement. The event's ID is a name-based UUID of the Customer
 * and {@code lastChangedAt}, and its version {@code lastChangedAt} in epoch milliseconds.
 */
@Component
class KafkaAbandonedCartPublisher implements AbandonedCartPublisher {

  static final String TOPIC = "cart.cart";
  private static final long SEND_TIMEOUT_SECONDS = 30;

  private final KafkaOperations<String, Object> kafka;
  private final TimeSource time;

  KafkaAbandonedCartPublisher(KafkaOperations<String, Object> kafka, TimeSource time) {
    this.kafka = kafka;
    this.time = time;
  }

  @Override
  public void publish(AbandonedCart abandoned) {
    try {
      kafka
          .send(TOPIC, abandoned.customerId(), eventOf(abandoned))
          .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted publishing an abandoned Cart", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException(
          "Kafka didn't take the abandoned Cart of " + abandoned.customerId(), e);
    }
  }

  private Map<String, Object> eventOf(AbandonedCart abandoned) {
    var lastChangedAt = abandoned.lastChangedAt();
    var event = new LinkedHashMap<String, Object>();
    event.put(
        "eventId",
        UUID.nameUUIDFromBytes(
                (abandoned.customerId() + "@" + lastChangedAt).getBytes(StandardCharsets.UTF_8))
            .toString());
    event.put("occurredAt", time.now().toString());
    event.put("customerId", abandoned.customerId());
    event.put("version", lastChangedAt.toEpochMilli());
    event.put("change", "ABANDONED");
    event.put(
        "cart",
        Map.of(
            "items",
            abandoned.cart().items().stream()
                .map(item -> Map.of("variantId", item.variantId(), "quantity", item.quantity()))
                .toList(),
            "lastChangedAt",
            lastChangedAt.toString()));
    return event;
  }
}
