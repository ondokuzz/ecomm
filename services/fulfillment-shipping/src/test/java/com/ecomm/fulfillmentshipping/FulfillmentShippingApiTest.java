package com.ecomm.fulfillmentshipping;

import com.ecomm.commons.security.FakeKeycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for HTTP-seam tests: the app runs with its own configuration, the stack's Warehouses and
 * mock carrier included, against one Postgres container shared by every test class, with {@link
 * FakeKeycloak} tokens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class FulfillmentShippingApiTest {

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("fulfillment_shipping");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  static final String STAFF = FakeKeycloak.token("staff-7", "STAFF");
  static final String CHECKOUT = FakeKeycloak.token("checkout", "CHECKOUT");
  static final String CUSTOMER = FakeKeycloak.token("customer-42", "CUSTOMER");

  @Autowired RestTestClient http;
}
