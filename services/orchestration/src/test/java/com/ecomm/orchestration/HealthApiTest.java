package com.ecomm.orchestration;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Orchestration serves its health and nothing else over HTTP. */
class HealthApiTest extends OrchestrationTest {

  @Autowired RestTestClient http;

  @Test
  void healthIsPublicAndUp() {
    http.get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  void thereIsNoOtherEndpoint() {
    http.get()
        .uri("/checkout")
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("staff-1", "STAFF")))
        .exchange()
        .expectStatus()
        .isNotFound();
  }
}
