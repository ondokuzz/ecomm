package com.ecomm.template;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * What every test's application runs against: a Postgres container shared by every test class, the
 * shared Kafka and schema registry from {@link EventBackbone}, and {@link FakeKeycloak}. A
 * test-only migration adds the tables the Greeting fixtures use. The Greeting topics' test schemas
 * are registered, and the Greeting consumer groups' dead-letter and redrive topics created. {@link
 * #LATE_GREETINGS} has no topic until a test creates it.
 */
final class TestInfrastructure {

  static final String GREETINGS = "service-template.greeting";
  static final String LATE_GREETINGS = "service-template.late-greeting";
  static final String GREETINGS_GROUP = "service-template.greetings";
  static final String METRICS_GROUP = "service-template.metrics";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("service_template");

  static {
    POSTGRES.start();
    EventBackbone.createTopic(GREETINGS);
    EventBackbone.createDeadLetterTopicsOf(GREETINGS_GROUP);
    EventBackbone.createDeadLetterTopicsOf(METRICS_GROUP);
    EventBackbone.registerSchemaOf(GREETINGS);
    EventBackbone.registerSchemaOf(LATE_GREETINGS);
  }

  private TestInfrastructure() {}

  static void registerWith(DynamicPropertyRegistry registry) {
    registerAllButTheDatabasePasswordWith(registry);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  /** As {@link #registerWith}, for a test that gives the application the password another way. */
  static void registerAllButTheDatabasePasswordWith(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/testdata");
  }

  static String databasePassword() {
    return POSTGRES.getPassword();
  }
}
