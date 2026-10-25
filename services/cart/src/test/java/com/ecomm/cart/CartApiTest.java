package com.ecomm.cart;

import com.ecomm.cart.application.port.in.DetectAbandonedCartsUseCase;
import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * Base for HTTP-seam tests: the app runs against one Redis container shared by every test class,
 * and the shared Kafka and schema registry from {@link EventBackbone}, with the Cart topic created
 * and its schema registered as the stack does. Each test acts as a Customer of its own, so no test
 * sees another's Cart.
 *
 * <p>The clock is a {@link TestTimeSource} and the scheduled abandonment sweep is off, so a test
 * places a change in time and sweeps when it chooses.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "ecomm.cart.abandonment-sweeper.enabled=false")
@AutoConfigureRestTestClient
@Import(TestTimeSource.class)
abstract class CartApiTest {

  static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

  /** The topic abandoned Carts are published to. */
  static final String CART_TOPIC = "cart.cart";

  static {
    REDIS.start();
    EventBackbone.createTopic(CART_TOPIC);
    EventBackbone.registerSchemaOf(CART_TOPIC);
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  /** Past the default abandonment threshold of 24 hours. */
  static final Duration A_DAY_AND_A_MINUTE = Duration.ofHours(24).plusMinutes(1);

  @Autowired RestTestClient http;
  @Autowired TestTimeSource clock;
  @Autowired DetectAbandonedCartsUseCase abandonment;

  @AfterEach
  void resetClock() {
    clock.reset();
  }

  static String tokenFor(String customerId) {
    return FakeKeycloak.token(customerId, "CUSTOMER");
  }

  RestTestClient.ResponseSpec setQuantity(String customerId, String variantId, String body) {
    return http.put()
        .uri("/cart/items/{variantId}", variantId)
        .headers(h -> h.setBearerAuth(tokenFor(customerId)))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  RestTestClient.ResponseSpec setQuantity(String customerId, String variantId, int quantity) {
    return setQuantity(customerId, variantId, "{\"quantity\": %d}".formatted(quantity));
  }

  List<CartItemView> itemsOf(String customerId) {
    return http.get()
        .uri("/cart")
        .headers(h -> h.setBearerAuth(tokenFor(customerId)))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(CartView.class)
        .returnResult()
        .getResponseBody()
        .items();
  }

  /** The parts of a Cart a client reads, independent of the service's classes. */
  record CartView(List<CartItemView> items) {}

  record CartItemView(String variantId, int quantity) {}
}
