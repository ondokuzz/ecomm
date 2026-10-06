package com.ecomm.reviewsratings;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * What every test's application runs against: a Mongo container shared by every test class, the
 * shared Kafka and schema registry from {@link EventBackbone} with the two topics Reviews consumes,
 * and {@link FakeKeycloak}.
 */
final class TestInfrastructure {

  private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

  static {
    MONGO.start();
    for (var topic : Events.TOPICS) {
      EventBackbone.createTopic(topic);
    }
  }

  private TestInfrastructure() {}

  static void registerWith(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl("reviews"));
  }
}
