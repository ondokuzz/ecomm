package com.ecomm.template;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Token validation and role checks, with failures rendered as problem details. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(SecurityApiTest.StaffOnlyController.class)
class SecurityApiTest {

  /** A role-restricted endpoint, the way a service guards Staff-only operations. */
  @RestController
  static class StaffOnlyController {

    @GetMapping("/staff-only")
    @PreAuthorize("hasRole('STAFF')")
    String staffOnly() {
      return "ok";
    }
  }

  @DynamicPropertySource
  static void keycloak(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
  }

  @Autowired RestTestClient http;

  @Test
  void aTokenSignedByAnUnknownKeyIsUnauthorized() {
    expectUnauthorized(FakeKeycloak.tokenSignedByUnknownKey("customer-42"));
  }

  @Test
  void aTokenFromAnotherIssuerIsUnauthorized() {
    expectUnauthorized(
        FakeKeycloak.tokenFromIssuer("http://keycloak:8080/realms/ecomm", "customer-42"));
  }

  @Test
  void aMalformedTokenIsUnauthorized() {
    expectUnauthorized("not-a-jwt");
  }

  @Test
  void aCustomerIsForbiddenFromAStaffOnlyEndpoint() {
    http.get()
        .uri("/staff-only")
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-42", "CUSTOMER")))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(403)
        .jsonPath("$.title")
        .isEqualTo("Forbidden");
  }

  @Test
  void staffCanReachAStaffOnlyEndpoint() {
    http.get()
        .uri("/staff-only")
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("staff-7", "STAFF")))
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void healthNeedsNoToken() {
    http.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }

  private void expectUnauthorized(String token) {
    http.get()
        .uri("/ping")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.title")
        .isEqualTo("Unauthorized");
  }
}
