package com.ecomm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.ecomm.commons.security.FakeKeycloak;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Base for the gateway's HTTP-seam tests. One WireMock server stands in for every routed service;
 * each test starts with it answering 200 to anything.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class GatewayApiTest {

  static final String CORRELATION_ID = "X-Correlation-Id";

  static final WireMockServer DOWNSTREAM = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    DOWNSTREAM.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    for (var service :
        List.of("catalog", "inventory", "cart", "checkout-pricing", "order-management")) {
      registry.add("ecomm.gateway.services." + service + ".uri", DOWNSTREAM::baseUrl);
    }
  }

  @Autowired RestTestClient http;

  @BeforeEach
  void resetDownstream() {
    DOWNSTREAM.resetAll();
    DOWNSTREAM.stubFor(any(anyUrl()).willReturn(ok("from downstream")));
  }

  static String customerToken() {
    return FakeKeycloak.token("customer-42", "CUSTOMER");
  }

  /** Every request that reached a service through the gateway. */
  static List<LoggedRequest> forwarded() {
    return DOWNSTREAM.findAll(anyRequestedFor(anyUrl()));
  }
}
