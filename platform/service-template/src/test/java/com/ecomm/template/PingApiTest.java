package com.ecomm.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class PingApiTest {

  /** The response as a client sees it, independent of the service's own classes. */
  record PingResponse(String service, Instant at) {}

  @Autowired RestTestClient http;

  @Test
  void pingAnswersWithTheServiceName() {
    var pong =
        http.get()
            .uri("/ping")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(PingResponse.class)
            .returnResult()
            .getResponseBody();

    assertThat(pong.service()).isEqualTo("service-template");
    assertThat(pong.at()).isNotNull();
  }
}
