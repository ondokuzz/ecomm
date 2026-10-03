package com.ecomm.template;

import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.commons.events.IntegrationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test-only: what a use case does with its change and its event. It saves a Greeting and publishes
 * its event in one transaction, as a service's use case does through its {@code Transactions} port.
 */
class Greetings {

  /** The event a Greeting's topic carries: its ID, version, change and state. */
  record GreetingEvent(String greetingId, long version, String change, Greeting greeting)
      implements IntegrationEvent {

    @Override
    public String topic() {
      return TestInfrastructure.GREETINGS;
    }

    @Override
    public String aggregateId() {
      return greetingId;
    }
  }

  /** The same event, for a topic that doesn't exist until a test creates it. */
  record LateGreetingEvent(String greetingId, long version, String change, Greeting greeting)
      implements IntegrationEvent {

    @Override
    public String topic() {
      return TestInfrastructure.LATE_GREETINGS;
    }

    @Override
    public String aggregateId() {
      return greetingId;
    }
  }

  record Greeting(String text) {}

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final IntegrationEventPublisher events;

  Greetings(JdbcTemplate jdbc, TransactionTemplate transactions, IntegrationEventPublisher events) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.events = events;
  }

  void save(String id, String text) {
    inTransaction(id, text, new GreetingEvent(id, 1, "CREATED", new Greeting(text)), false);
  }

  /** Saves and publishes, then fails, so the transaction rolls back. */
  void saveThenFail(String id, String text) {
    inTransaction(id, text, new GreetingEvent(id, 1, "CREATED", new Greeting(text)), true);
  }

  void saveToLateTopic(String id, String text) {
    inTransaction(id, text, new LateGreetingEvent(id, 1, "CREATED", new Greeting(text)), false);
  }

  boolean exists(String id) {
    return jdbc.queryForObject("SELECT count(*) FROM greeting WHERE id = ?", Long.class, id) > 0;
  }

  private void inTransaction(String id, String text, IntegrationEvent event, boolean fail) {
    transactions.executeWithoutResult(
        status -> {
          jdbc.update("INSERT INTO greeting (id, text) VALUES (?, ?)", id, text);
          events.publish(event);
          if (fail) {
            throw new IllegalStateException("The use case failed after publishing");
          }
        });
  }
}
