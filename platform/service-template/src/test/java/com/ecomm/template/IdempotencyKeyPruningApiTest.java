package com.ecomm.template;

import static org.awaitility.Awaitility.await;

import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Idempotency keys are pruned once older than {@code ecomm.idempotency.keep-for} (7 days, swept
 * daily), here shortened to seconds. A pruned key can carry a new request.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"ecomm.idempotency.keep-for=1s", "ecomm.idempotency.prune-every=500ms"})
@AutoConfigureRestTestClient
@Import(GreetingCommands.class)
class IdempotencyKeyPruningApiTest {

  private static final String CUSTOMER = FakeKeycloak.token("customer-42", "CUSTOMER");

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;

  @Test
  void aKeyOlderThanItsRetentionIsPrunedAndCanCarryANewRequest() {
    var key = "key-" + UUID.randomUUID();
    create(key);

    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(500))
        .until(() -> create(key) == 201);
  }

  /** Creates a Greeting with a new text under {@code key}, returning the status. */
  private int create(String key) {
    return http.post()
        .uri("/greetings")
        .headers(
            h -> {
              h.setBearerAuth(CUSTOMER);
              h.set("Idempotency-Key", key);
            })
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("text", "Hello " + UUID.randomUUID()))
        .exchange()
        .returnResult(String.class)
        .getStatus()
        .value();
  }
}
