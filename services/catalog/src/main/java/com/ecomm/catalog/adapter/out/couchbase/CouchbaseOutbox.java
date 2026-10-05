package com.ecomm.catalog.adapter.out.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.commons.web.CorrelationId;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Catalog's {@link IntegrationEventPublisher}: each event becomes an outbox document, written in
 * the caller's Couchbase transaction, so it is stored if and only if the change it describes is.
 * {@link OutboxRelay} sends it to Kafka afterwards (ADR 0002).
 *
 * <p>An outbox document, marked {@code "type": "outbox"} and keyed {@code outbox::<eventId>}, holds
 * the topic, the key, the aggregate's version, the Correlation ID of the request that caused it,
 * when it was written, and the event's JSON: its record's components plus {@code eventId} and
 * {@code occurredAt}, with an absent optional field left out.
 */
@Component
class CouchbaseOutbox implements IntegrationEventPublisher {

  static final String TYPE = "outbox";

  private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

  private final Collection collection;
  private final CouchbaseTransactions transactions;
  private final JsonMapper json;
  private final Clock clock;

  CouchbaseOutbox(
      Cluster cluster,
      @Value("${ecomm.catalog.bucket}") String bucketName,
      CouchbaseTransactions transactions,
      JsonMapper json,
      ObjectProvider<Clock> clock) {
    var bucket = cluster.bucket(bucketName);
    bucket.waitUntilReady(Duration.ofSeconds(60));
    this.collection = bucket.defaultCollection();
    this.transactions = transactions;
    this.json =
        json.rebuild()
            .changeDefaultPropertyInclusion(
                inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_ABSENT))
            .build();
    this.clock = clock.getIfAvailable(Clock::systemUTC);
  }

  @Override
  public void publish(IntegrationEvent event) {
    var context = transactions.required("Publish a " + event.topic() + " event");
    var eventId = UUID.randomUUID().toString();
    var now = clock.instant();
    var value = new LinkedHashMap<String, Object>();
    value.put("eventId", eventId);
    value.put("occurredAt", now.toString());
    value.putAll(json.convertValue(event, JSON_OBJECT));
    var document =
        JsonObject.create()
            .put("type", TYPE)
            .put("topic", event.topic())
            .put("key", event.aggregateId())
            .put("version", event.version())
            .put("createdAt", now.toEpochMilli())
            .put("value", JsonObject.from(value));
    CorrelationId.current().ifPresent(id -> document.put("correlationId", id));
    context.insert(collection, TYPE + "::" + eventId, document);
  }
}
