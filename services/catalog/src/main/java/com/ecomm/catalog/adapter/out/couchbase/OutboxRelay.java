package com.ecomm.catalog.adapter.out.couchbase;

import static com.couchbase.client.java.kv.MutateInOptions.mutateInOptions;
import static com.couchbase.client.java.query.QueryOptions.queryOptions;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.MutateInSpec;
import com.couchbase.client.java.query.QueryScanConsistency;
import com.ecomm.commons.web.CorrelationId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;

/**
 * Sends the outbox's documents to Kafka and marks each one sent once Kafka has acknowledged it. On
 * a fixed interval it takes the oldest unsent document of each aggregate (the lowest version, so
 * one aggregate's events go out in order), sends them, and repeats while that sends anything. It
 * finds them through a query index created on startup if missing.
 *
 * <p>Delivery is at least once: a document whose send fails stays unsent and is tried again, and
 * one sent but not yet marked (or relayed by two instances at once) may be sent twice.
 *
 * <ul>
 *   <li>A send that times out means Kafka, or that topic, can't be reached. Its aggregate waits for
 *       the next pass, and after {@value #TIMEOUTS_PER_PASS} timeouts the pass stops, since Kafka
 *       is likely down.
 *   <li>A document Kafka refuses, such as one that fails its schema, is logged at error level and
 *       holds back only its own aggregate's later events.
 * </ul>
 *
 * Sent documents expire a week later.
 */
@Component
class OutboxRelay implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private static final int AGGREGATES_PER_PASS = 100;
  private static final int TIMEOUTS_PER_PASS = 3;
  private static final Duration SEND_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration KEEP_SENT = Duration.ofDays(7);

  private final Scope scope;
  private final Collection collection;
  private final KafkaOperations<String, Object> kafka;
  private final Duration interval;
  private ScheduledExecutorService scheduler;

  OutboxRelay(
      Cluster cluster,
      @Value("${ecomm.catalog.bucket}") String bucketName,
      KafkaOperations<String, Object> kafka,
      @Value("${ecomm.catalog.outbox.relay-every:500ms}") Duration interval) {
    var bucket = cluster.bucket(bucketName);
    bucket.waitUntilReady(Duration.ofSeconds(60));
    this.scope = bucket.defaultScope();
    this.collection = bucket.defaultCollection();
    this.kafka = kafka;
    this.interval = interval;
    scope.query(
        "CREATE INDEX idx_outbox_pending IF NOT EXISTS ON `_default`(topic, `key`, version)"
            + " WHERE type = 'outbox' AND sentAt IS MISSING");
  }

  @Override
  public void start() {
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("outbox-relay").daemon().factory());
    scheduler.scheduleWithFixedDelay(this::relay, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** Sends what is unsent, pass after pass, until a pass sends nothing. */
  void relay() {
    try {
      while (pass()) {
        // Each pass sends one document per aggregate; an aggregate with more goes again.
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException e) {
      log.warn("Reading the outbox failed", e);
    }
  }

  /** Sends the oldest unsent document of each aggregate; returns whether it sent any. */
  private boolean pass() throws InterruptedException {
    var sent = false;
    var timeouts = 0;
    for (var id : oldestUnsentOfEachAggregate()) {
      var document = collection.get(id).contentAsObject();
      try {
        send(document);
        markSent(id);
        sent = true;
      } catch (TimeoutException | ExecutionException | RuntimeException e) {
        if (e instanceof TimeoutException || causedByTimeout(e)) {
          log.warn("Kafka didn't take outbox document {} in time; trying again later", id, e);
          if (++timeouts == TIMEOUTS_PER_PASS) {
            return false;
          }
          continue;
        }
        log.error(
            "Kafka refused outbox document {} for {}; it and its aggregate's later events stay"
                + " unsent",
            id,
            document.getString("topic"),
            e);
      }
    }
    return sent;
  }

  private List<String> oldestUnsentOfEachAggregate() {
    return scope
        .query(
            "SELECT RAW MIN([o.version, META(o).id])[1] FROM `_default` o"
                + " WHERE o.type = 'outbox' AND o.sentAt IS MISSING AND o.topic IS NOT MISSING"
                + " GROUP BY o.topic, o.`key` LIMIT $limit",
            queryOptions()
                .scanConsistency(QueryScanConsistency.REQUEST_PLUS)
                .parameters(JsonObject.create().put("limit", AGGREGATES_PER_PASS)))
        .rowsAs(String.class);
  }

  private void send(JsonObject document)
      throws InterruptedException, ExecutionException, TimeoutException {
    var record =
        new ProducerRecord<String, Object>(
            document.getString("topic"),
            document.getString("key"),
            document.getObject("value").toMap());
    var correlationId = document.getString("correlationId");
    if (correlationId != null) {
      record.headers().add(CorrelationId.HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
    }
    kafka.send(record).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** Whether Kafka failed for want of an answer, such as metadata it couldn't fetch. */
  private static boolean causedByTimeout(Throwable failure) {
    for (var cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof org.apache.kafka.common.errors.TimeoutException) {
        return true;
      }
    }
    return false;
  }

  private void markSent(String id) {
    collection.mutateIn(
        id,
        List.of(MutateInSpec.upsert("sentAt", Instant.now().toString())),
        mutateInOptions().expiry(KEEP_SENT));
  }

  @Override
  public void stop() {
    scheduler.shutdownNow();
    scheduler = null;
  }

  @Override
  public boolean isRunning() {
    return scheduler != null;
  }
}
