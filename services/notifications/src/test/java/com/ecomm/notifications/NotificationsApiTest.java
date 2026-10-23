package com.ecomm.notifications;

import static org.awaitility.Awaitility.await;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for HTTP-seam tests: the app runs against one Postgres container shared by every test class,
 * the shared Kafka and schema registry from {@link EventBackbone} with the topics Notifications
 * consumes and its groups' dead-letter and redrive topics, {@link FakeKeycloak} tokens, and
 * Keycloak's admin API and Fulfillment & Shipping's Warehouse read in WireMock ({@link Keycloak},
 * {@link FulfillmentShipping}). Email goes to {@link TestEmailProvider} instead of SMTP. The
 * dispatcher looks for due Notifications every 50 ms, and retries a transient failure after 20 ms,
 * doubling, so six attempts take well under a second.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "ecomm.notifications.email.smtp.enabled=false",
      "ecomm.notifications.dispatch.poll-every=50ms",
      "ecomm.notifications.dispatch.first-retry-after=20ms"
    })
@AutoConfigureRestTestClient
@Import(TestEmailProvider.class)
abstract class NotificationsApiTest {

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("notifications");

  static {
    POSTGRES.start();
    for (var topic : Events.TOPICS) {
      EventBackbone.createTopic(topic);
    }
    for (var group : Events.GROUPS) {
      EventBackbone.createDeadLetterTopicsOf(group);
    }
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    Keycloak.registerWith(registry);
    FulfillmentShipping.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  static final String STAFF = FakeKeycloak.token("staff-7", "STAFF");

  @Autowired RestTestClient http;
  @Autowired TestEmailProvider email;

  @BeforeEach
  void resetStubs() {
    Keycloak.reset();
    FulfillmentShipping.reset();
  }

  /** A new Customer's ID, as Keycloak's user IDs are. */
  static String newCustomerId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Publishes a Product with one Variant, {@code <sku>-V} with no axis values unless {@code axis}
   * and {@code value} are given, and waits until Notifications names it. Its consumer is apart from
   * the Orders', so until then a paid Order's lines would still go by Variant ID: each try pays a
   * throwaway Order for the Variant until its confirmation names the Product.
   */
  String projectedVariant(String name, String axis, String value) {
    var sku = newSku();
    var variantId = sku + "-V";
    var event = Events.product(sku, name, variantId);
    if (axis != null) {
      event.axis(variantId, axis, value);
    }
    event.publish();
    var probe = newCustomerId();
    var address = "probe-" + probe + "@example.com";
    Keycloak.customer(probe, address, "Probe", "Customer");
    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              var before = email.sentTo(address).size();
              Events.order(probe).line(variantId, 1, 100).placed().paid();
              await()
                  .atMost(Duration.ofSeconds(20))
                  .until(() -> email.sentTo(address).size() > before);
              return email.sentTo(address).getLast().message().text().contains(name);
            });
    return variantId;
  }

  /** A new email address, such as {@code ada-1f2e3d4c@example.com}, so tests don't share them. */
  static String newAddress(String name) {
    return name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
  }

  /** A new SKU, so tests don't share Products. */
  static String newSku() {
    return "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }
}
