package com.ecomm.fulfillmentshipping;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for HTTP-seam tests: the app runs with its own configuration, the stack's Warehouses and
 * mock carrier included, against one Postgres container shared by every test class and the shared
 * Kafka and schema registry from {@link EventBackbone}, with {@link FakeKeycloak} tokens. The
 * Shipment topic is created and its schema registered as the stack does, and so are Catalog's
 * Product topic and the Product projection's dead-letter and redrive topics. The mock carrier's
 * webhooks are recorded at once instead of delivered, for each test to deliver, repeat, reorder or
 * withhold.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(FulfillmentShippingApiTest.RecordingCarrierWebhooks.class)
abstract class FulfillmentShippingApiTest {

  /** The secret the Carrier signs its webhooks with, as the stack sets it. */
  static final String CARRIER_WEBHOOK_SECRET = "test-carrier-webhook-secret";

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("fulfillment_shipping");

  static {
    POSTGRES.start();
    EventBackbone.createTopic(ShipmentEvents.TOPIC);
    EventBackbone.registerSchemaOf(ShipmentEvents.TOPIC);
    EventBackbone.createTopic(Products.TOPIC);
    EventBackbone.createDeadLetterTopicsOf(Products.GROUP);
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add(
        "ecomm.fulfillment-shipping.carrier-webhooks.secret", () -> CARRIER_WEBHOOK_SECRET);
    // At once, a millisecond apart, so each Tracking event has its own time.
    registry.add("ecomm.fulfillment-shipping.mock-carrier.tracking-delays.in-transit", () -> "1ms");
    registry.add(
        "ecomm.fulfillment-shipping.mock-carrier.tracking-delays.out-for-delivery", () -> "2ms");
    registry.add("ecomm.fulfillment-shipping.mock-carrier.tracking-delays.delivered", () -> "3ms");
  }

  @TestConfiguration
  static class RecordingCarrierWebhooks {

    @Bean
    @Primary
    RecordedCarrierWebhooks recordedCarrierWebhooks() {
      return new RecordedCarrierWebhooks();
    }
  }

  static final String STAFF = FakeKeycloak.token("staff-7", "STAFF");
  static final String CHECKOUT = FakeKeycloak.token("checkout", "CHECKOUT");
  static final String ORCHESTRATION = FakeKeycloak.token("orchestration", "ORCHESTRATION");
  static final String CUSTOMER = FakeKeycloak.token("customer-42", "CUSTOMER");

  @Autowired RestTestClient http;

  @Autowired RecordedCarrierWebhooks carrierWebhooks;
}
