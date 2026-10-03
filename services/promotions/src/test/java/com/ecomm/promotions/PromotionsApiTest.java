package com.ecomm.promotions;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.ecomm.commons.security.FakeKeycloak;
import com.ecomm.promotions.application.port.out.TimeSource;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for HTTP-seam tests: the app runs against one Postgres container shared by every test class,
 * and one WireMock server stands in for Catalog, listing the Categories {@code phones} and {@code
 * audio} and the currencies EUR, USD and JPY. Coupons and Campaigns outlive each test, so each test
 * uses codes, names and priorities of its own. Each test starts at the real time.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PromotionsApiTest.Clocks.class)
abstract class PromotionsApiTest {

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("promotions");

  static final WireMockServer CATALOG = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    POSTGRES.start();
    CATALOG.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    registry.add("ecomm.promotions.catalog-url", CATALOG::baseUrl);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  /** Promotions' clock, which the tests set to a moment of their own, or leave at the real time. */
  static class MovableTimeSource implements TimeSource {

    private volatile Instant fixed;

    @Override
    public Instant now() {
      var at = fixed;
      return at == null ? Instant.now() : at;
    }

    void set(Instant at) {
      fixed = at;
    }
  }

  @TestConfiguration
  static class Clocks {

    @Bean
    @Primary
    MovableTimeSource movableTimeSource() {
      return new MovableTimeSource();
    }
  }

  @Autowired RestTestClient http;

  @Autowired MovableTimeSource time;

  @BeforeEach
  void catalogAndRealTime() {
    time.set(null);
    CATALOG.resetAll();
    CATALOG.stubFor(
        get("/categories")
            .willReturn(
                okJson(
                    """
                    [{"slug": "phones", "name": "Phones", "productCount": 3, "attributes": []},
                     {"slug": "audio", "name": "Audio", "productCount": 2, "attributes": []}]
                    """)));
    CATALOG.stubFor(
        get("/currencies")
            .willReturn(
                okJson(
                    """
                    [{"code": "EUR", "minorDigits": 2}, {"code": "USD", "minorDigits": 2},
                     {"code": "JPY", "minorDigits": 0}]
                    """)));
  }

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

  /** Creates the Campaign in the JSON {@code body} as Staff. */
  RestTestClient.ResponseSpec createCampaign(String body) {
    return http.post()
        .uri("/campaigns")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** Creates a Campaign that must succeed, and returns its ID. */
  String createdCampaign(String body) {
    return createCampaign(body)
        .expectStatus()
        .isCreated()
        .expectBody(CampaignId.class)
        .returnResult()
        .getResponseBody()
        .id();
  }

  RestTestClient.ResponseSpec readCampaign(String id) {
    return http.get()
        .uri("/campaigns/{id}", id)
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange();
  }

  RestTestClient.ResponseSpec updateCampaign(String id, String body) {
    return http.put()
        .uri("/campaigns/{id}", id)
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  RestTestClient.ResponseSpec deleteCampaign(String id) {
    return http.delete()
        .uri("/campaigns/{id}", id)
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange();
  }

  RestTestClient.ResponseSpec listCampaigns() {
    return http.get().uri("/campaigns").headers(h -> h.setBearerAuth(staffToken())).exchange();
  }

  /**
   * A Campaign for {@code percent}% off everything, with no minimum, active and valid from 2020
   * until 2100.
   */
  static String campaign(String name, int priority, int percent) {
    return """
        {"name": "%s", "discount": {"type": "PERCENT_OFF", "percentOff": %d}, "categories": [],
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z",
         "active": true, "priority": %d}
        """
        .formatted(name, percent, priority);
  }

  record CampaignId(String id) {}
}
