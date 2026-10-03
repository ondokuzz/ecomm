package com.ecomm.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class PingApiTest {

  /** The response as a client sees it, independent of the service's own classes. */
  record PingResponse(String service, String customerId, Instant at) {}

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;

  @Test
  void pingRequiresAuthentication() {
    http.get()
        .uri("/ping")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401);
  }

  @Test
  void pingAnswersWithTheServiceNameAndTheCallingCustomer() {
    var pong =
        http.get()
            .uri("/ping")
            .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-42", "CUSTOMER")))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(PingResponse.class)
            .returnResult()
            .getResponseBody();

    assertThat(pong.service()).isEqualTo("service-template");
    assertThat(pong.customerId()).isEqualTo("customer-42");
    assertThat(pong.at()).isNotNull();
  }
}
