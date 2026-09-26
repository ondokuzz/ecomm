package com.ecomm.cart;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * Base for HTTP-seam tests: the app runs against one Redis container shared by every test class.
 * Each test acts as a Customer of its own, so no test sees another's Cart.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class CartApiTest {

  static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

  static {
    REDIS.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired RestTestClient http;

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
