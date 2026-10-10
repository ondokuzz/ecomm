package com.ecomm.template;

import com.ecomm.commons.events.Versions;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test-only: a consumer as a service writes one. It keeps the newest version of each Greeting it
 * has seen, reads only the fields it needs, and fails on the text {@code boom} until a test fixes
 * that Greeting. A test that needs the Greetings to itself sets {@code greetings.group-id}.
 */
class GreetingProjection {

  private static final Logger log = LoggerFactory.getLogger(GreetingProjection.class);

  /**
   * The fields this consumer needs; the event's others are ignored. One it can't do without is
   * required, so an event missing it can't be read, and is dead-lettered without retries.
   */
  record GreetingReceived(
      @JsonProperty(required = true) String greetingId,
      @JsonProperty(required = true) long version,
      @JsonProperty(required = true) Greeting greeting) {
    record Greeting(String text) {}
  }

  record Projected(long version, String text) {}

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
  private final Set<String> fixed = ConcurrentHashMap.newKeySet();

  GreetingProjection(JdbcTemplate jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  @KafkaListener(
      topics = TestInfrastructure.GREETINGS,
      groupId = "${greetings.group-id:service-template.greetings}")
  void on(GreetingReceived event) {
    if ("boom".equals(event.greeting().text()) && !fixed.contains(event.greetingId())) {
      failedAttempts.merge(event.greetingId(), 1, Integer::sum);
      throw new IllegalStateException("Cannot apply this Greeting");
    }
    var applied =
        transactions.execute(
            status ->
                Versions.applyIfNewer(
                    event.version(),
                    find(event.greetingId()),
                    Projected::version,
                    () -> upsert(event)));
    log.info(
        "{} greeting {} version {}",
        applied ? "Applied" : "Ignored",
        event.greetingId(),
        event.version());
  }

  /** From now on, applies the Greeting even when its text is {@code boom}. */
  void fix(String greetingId) {
    fixed.add(greetingId);
  }

  int attemptsOn(String greetingId) {
    return failedAttempts.getOrDefault(greetingId, 0);
  }

  Optional<Projected> find(String greetingId) {
    return jdbc
        .query(
            "SELECT version, text FROM greeting_projection WHERE greeting_id = ?",
            (rs, row) -> new Projected(rs.getLong("version"), rs.getString("text")),
            greetingId)
        .stream()
        .findFirst();
  }

  private void upsert(GreetingReceived event) {
    jdbc.update(
        """
        INSERT INTO greeting_projection (greeting_id, version, text) VALUES (?, ?, ?)
        ON CONFLICT (greeting_id) DO UPDATE SET version = excluded.version, text = excluded.text
        """,
        event.greetingId(),
        event.version(),
        event.greeting().text());
  }
}
