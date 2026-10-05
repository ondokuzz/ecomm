package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.catalog.application.port.out.Transactions;
import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.commons.events.IntegrationEventPublisher;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * The outbox keeps an event until Kafka takes it. Here Kafka refuses a send because its topic
 * doesn't exist yet, which fails a send the way an unreachable broker does: the event waits, other
 * aggregates' events still go out, and once the topic exists it is sent, in order with the events
 * written after it.
 */
class OutboxRelayApiTest extends CatalogApiTest {

  /** A topic no test creates until it wants sends to it to succeed. */
  private static final String LATE_TOPIC = "catalog.late-test";

  static {
    EventBackbone.registerSchemaOf(LATE_TOPIC);
  }

  @Autowired Transactions transactions;
  @Autowired IntegrationEventPublisher events;

  record LateEvent(String id, long version) implements IntegrationEvent {

    @Override
    public String topic() {
      return LATE_TOPIC;
    }

    @Override
    public String aggregateId() {
      return id;
    }
  }

  @Test
  void anEventKafkaRefusesIsSentOnceItCanAndNothingIsLost() {
    var id = "late-" + UUID.randomUUID();
    publish(new LateEvent(id, 1));
    publish(new LateEvent(id, 2));
    var slug = "relay-" + UUID.randomUUID().toString().substring(0, 8);

    http.post()
        .uri("/categories")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"slug\": \"%s\", \"name\": \"Relayed\", \"attributes\": []}".formatted(slug))
        .exchange()
        .expectStatus()
        .isCreated();

    assertThat(CatalogEvents.keyed(CATEGORY_TOPIC, slug, 1)).hasSize(1);
    EventBackbone.createTopic(LATE_TOPIC);
    var late = CatalogEvents.keyed(LATE_TOPIC, id, 2, Duration.ofSeconds(30));
    assertThat(CatalogEvents.valuesOf(late))
        .extracting(e -> e.get("version").asLong())
        .startsWith(1L, 2L)
        .containsOnly(1L, 2L);
  }

  @Test
  void publishingOutsideATransactionIsRefused() {
    assertThatThrownBy(() -> events.publish(new LateEvent("outside", 1)))
        .isInstanceOf(IllegalStateException.class);
  }

  private void publish(LateEvent event) {
    transactions.inTransaction(
        () -> {
          events.publish(event);
          return event;
        });
  }
}
