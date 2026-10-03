package com.ecomm.commons.events.outbox;

import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.commons.web.CorrelationId;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Postgres services' {@link IntegrationEventPublisher}: Spring Modulith's event publication
 * registry is the outbox, written in the caller's transaction, and its Kafka externalization sends
 * each publication to its topic once the transaction commits (ADR 0002).
 *
 * <p>The event's JSON is its record's components plus {@code eventId} and {@code occurredAt}. An
 * empty {@code Optional} or a {@code null} component is left out, so an absent optional field is
 * missing rather than {@code null}.
 */
public class OutboxIntegrationEventPublisher implements IntegrationEventPublisher {

  private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

  private final ApplicationEventPublisher events;
  private final JsonMapper json;
  private final Clock clock;

  public OutboxIntegrationEventPublisher(
      ApplicationEventPublisher events, JsonMapper json, Clock clock) {
    this.events = events;
    this.json =
        json.rebuild()
            .changeDefaultPropertyInclusion(
                inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_ABSENT))
            .build();
    this.clock = clock;
  }

  @Override
  public void publish(IntegrationEvent event) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "Publish "
              + event.topic()
              + " events inside the transaction of the change they describe");
    }
    var value = new LinkedHashMap<String, Object>();
    value.put("eventId", UUID.randomUUID().toString());
    value.put("occurredAt", clock.instant().toString());
    value.putAll(json.convertValue(event, JSON_OBJECT));
    events.publishEvent(
        new OutboxedEvent(
            event.topic(), event.aggregateId(), CorrelationId.current().orElse(null), value));
  }
}
