package com.ecomm.template;

import com.ecomm.commons.events.Versions;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test-only: a consumer as a service writes one. It keeps the newest version of each Greeting it
 * has seen, reads only the fields it needs, and fails on the text {@code boom}. A test that needs
 * the Greetings to itself sets {@code greetings.group-id}.
 */
class GreetingProjection {

  private static final Logger log = LoggerFactory.getLogger(GreetingProjection.class);

  /** The fields this consumer needs; the event's others are ignored. */
  record GreetingReceived(String greetingId, long version, Greeting greeting) {
    record Greeting(String text) {}
  }

  record Projected(long version, String text) {}

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();

  GreetingProjection(JdbcTemplate jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  @KafkaListener(
      topics = TestInfrastructure.GREETINGS,
      groupId = "${greetings.group-id:service-template.greetings}")
  void on(GreetingReceived event) {
    if ("boom".equals(event.greeting().text())) {
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
