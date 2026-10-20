package com.ecomm.returnswarranty;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for HTTP-seam tests: the app runs with its own configuration, the store's Return Period
 * included, against one Postgres container shared by every test class and the shared Kafka and
 * schema registry from {@link EventBackbone}, with {@link FakeKeycloak} tokens. Fulfillment &
 * Shipping's Shipment topic, Catalog's Product topic and Order Management's Order topic are created
 * as the stack creates them, with the projections' dead-letter and redrive topics, and so is the
 * RMA topic, its schema registered. The clock is fixed at {@link TestTimeSource#NOW}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestTimeSource.class)
abstract class ReturnsWarrantyApiTest {

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("returns_warranty");

  /** The topic Returns & Warranty publishes each change to an RMA to. */
  static final String RMA_TOPIC = "returns-warranty.rma";

  static {
    POSTGRES.start();
    EventBackbone.createTopic(RMA_TOPIC);
    EventBackbone.registerSchemaOf(RMA_TOPIC);
    EventBackbone.createTopic(Shipments.TOPIC);
    EventBackbone.createDeadLetterTopicsOf(Shipments.GROUP);
    EventBackbone.createTopic(Products.TOPIC);
    EventBackbone.createDeadLetterTopicsOf(Products.GROUP);
    EventBackbone.createTopic(Orders.TOPIC);
    EventBackbone.createDeadLetterTopicsOf(Orders.GROUP);
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  static final String CUSTOMER_ID = "customer-42";
  static final String CUSTOMER = FakeKeycloak.token(CUSTOMER_ID, "CUSTOMER");
  static final String OTHER_CUSTOMER = FakeKeycloak.token("customer-43", "CUSTOMER");
  static final String STAFF = FakeKeycloak.token("staff-7", "STAFF");
  static final String ORCHESTRATION = FakeKeycloak.token("orchestration", "ORCHESTRATION");

  @Autowired RestTestClient http;
}
