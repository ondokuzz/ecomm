package com.ecomm.promotions;

import com.ecomm.commons.security.FakeKeycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for HTTP-seam tests: the app runs against one Postgres container shared by every test class.
 * Coupons outlive each test, so each test uses codes of its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class PromotionsApiTest {

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("promotions");

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

  @Autowired RestTestClient http;

  static String staffToken() {
    return FakeKeycloak.token("staff-7", "STAFF");
  }

  static String customerToken() {
    return FakeKeycloak.token("customer-42", "CUSTOMER");
  }

  /** Checkout's own token: the only caller allowed to evaluate a Coupon. */
  static String checkoutToken() {
    return FakeKeycloak.token("checkout", "CHECKOUT");
  }

  /** Creates the Coupon in the JSON {@code body} as Staff. */
  RestTestClient.ResponseSpec create(String body) {
    return create(staffToken(), body);
  }

  RestTestClient.ResponseSpec create(String token, String body) {
    return http.post()
        .uri("/coupons")
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** Creates a Coupon that must succeed. */
  void created(String body) {
    create(body).expectStatus().isCreated();
  }

  RestTestClient.ResponseSpec read(String code) {
    return http.get()
        .uri("/coupons/{code}", code)
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange();
  }

  /** Replaces the Coupon with {@code code} as Staff. */
  RestTestClient.ResponseSpec update(String code, String body) {
    return http.put()
        .uri("/coupons/{code}", code)
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  RestTestClient.ResponseSpec delete(String code) {
    return http.delete()
        .uri("/coupons/{code}", code)
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange();
  }

  /**
   * Evaluates {@code couponCode} against {@code subtotalMinor} of {@code currency}, as Checkout.
   */
  RestTestClient.ResponseSpec evaluate(String couponCode, long subtotalMinor, String currency) {
    return evaluate(
        checkoutToken(),
        """
        {"couponCode": "%s", "subtotal": {"amountMinor": %d, "currency": "%s"}}
        """
            .formatted(couponCode, subtotalMinor, currency));
  }

  RestTestClient.ResponseSpec evaluate(String token, String body) {
    return http.post()
        .uri("/discounts/evaluate")
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** A Coupon for {@code percent}% off, active and valid from 2020 until 2100. */
  static String percentOff(String code, int percent) {
    return """
        {"code": "%s", "discount": {"type": "PERCENT_OFF", "percentOff": %d},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """
        .formatted(code, percent);
  }
}
