package com.ecomm.template;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * What every test's application runs against: a Postgres container shared by every test class, the
 * shared Kafka and schema registry from {@link EventBackbone}, and {@link FakeKeycloak}. A
 * test-only migration adds the tables the Greeting fixtures use, and the Greeting topics' test
 * schemas are registered. {@link #LATE_GREETINGS} has no topic until a test creates it.
 */
final class TestInfrastructure {

  static final String GREETINGS = "service-template.greeting";
  static final String LATE_GREETINGS = "service-template.late-greeting";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("service_template");

  static {
    POSTGRES.start();
    EventBackbone.createTopic(GREETINGS);
    EventBackbone.registerSchemaOf(GREETINGS);
    EventBackbone.registerSchemaOf(LATE_GREETINGS);
  }

  private TestInfrastructure() {}

  static void registerWith(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/testdata");
  }
}
