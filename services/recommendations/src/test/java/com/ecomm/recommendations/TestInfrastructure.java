package com.ecomm.recommendations;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * What every test's application runs against: a Postgres container shared by every test class, the
 * shared Kafka and schema registry from {@link EventBackbone} with the two topics Recommendations
 * consumes and its consumer groups' dead-letter and redrive topics, and {@link FakeKeycloak}. Each
 * topic has one partition, so its events are applied in the order they were sent.
 */
final class TestInfrastructure {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("recommendations");

  static {
    POSTGRES.start();
    for (var topic : Events.TOPICS) {
      EventBackbone.createTopic(topic);
    }
    for (var group : Events.GROUPS) {
      EventBackbone.createDeadLetterTopicsOf(group);
    }
  }

  private TestInfrastructure() {}

  static void registerWith(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
