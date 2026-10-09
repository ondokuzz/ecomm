package com.ecomm.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Commands that take an {@code Idempotency-Key}: a repeat from the same caller gets the first
 * response back and acts once. Exercised through the test-only {@link GreetingCommands}, each of
 * which creates a Greeting with a new ID.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(GreetingCommands.class)
class IdempotencyApiTest {

  private static final JsonMapper JSON = new JsonMapper();
  private static final String CUSTOMER = FakeKeycloak.token("customer-42", "CUSTOMER");

  /** A response as a client sees it. */
  record Response(int status, HttpHeaders headers, JsonNode body) {

    boolean replayed() {
      return "true".equals(headers.getFirst("Idempotent-Replayed"));
    }

    String greetingId() {
      return body.get("id").asString();
    }

    String reason() {
      return body.get("reason").asString();
    }
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;

  @Test
  void aRepeatReplaysTheFirstResponseAndActsOnce() {
    var key = newKey();
    var text = newText();

    var first = create(CUSTOMER, key, text);
    var repeat = create(CUSTOMER, key, text);

    assertThat(first.status()).isEqualTo(201);
    assertThat(first.replayed()).isFalse();
    assertThat(repeat.status()).isEqualTo(201);
    assertThat(repeat.replayed()).isTrue();
    assertThat(repeat.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(repeat.headers().getLocation()).isEqualTo(first.headers().getLocation());
    assertThat(repeat.body()).isEqualTo(first.body());
    assertThat(greetingsWithText(text)).containsExactly(first.greetingId());
  }

  @Test
  void theSameKeyWithADifferentBodyIsRefused() {
    var key = newKey();
    create(CUSTOMER, key, newText());
    var text = newText();

    var reuse = create(CUSTOMER, key, text);

    assertThat(reuse.status()).isEqualTo(422);
    assertThat(reuse.headers().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(reuse.reason()).isEqualTo("idempotencyKeyReused");
    assertThat(greetingsWithText(text)).isEmpty();
  }

  @Test
  void theSameKeyOnADifferentPathIsRefused() {
    var key = newKey();
    var text = newText();
    create(CUSTOMER, key, text);

    var reuse = post("/greetings/drafts", CUSTOMER, key, text);

    assertThat(reuse.status()).isEqualTo(422);
    assertThat(reuse.reason()).isEqualTo("idempotencyKeyReused");
  }

  @Test
  void concurrentRequestsWithOneKeyActOnceAndTheLaterReplaysTheFirst() throws Exception {
    var key = newKey();
    var text = "slow " + UUID.randomUUID(); // the command takes a second, so the two overlap

    var one = CompletableFuture.supplyAsync(() -> create(CUSTOMER, key, text));
    var other = CompletableFuture.supplyAsync(() -> create(CUSTOMER, key, text));
    var responses = List.of(one.get(), other.get());

    assertThat(responses).extracting(Response::status).containsExactly(201, 201);
    assertThat(responses).filteredOn(Response::replayed).hasSize(1);
    assertThat(responses.get(0).body()).isEqualTo(responses.get(1).body());
    assertThat(greetingsWithText(text)).containsExactly(responses.get(0).greetingId());
  }

  @Test
  void aRefusalRecordsNothingSoARepeatIsEvaluatedAfresh() {
    var text = newText();
    var existing = create(CUSTOMER, newKey(), text);
    var key = newKey();

    var refused = create(CUSTOMER, key, text);
    deleteGreeting(existing.greetingId());
    var repeat = create(CUSTOMER, key, text);

    assertThat(refused.status()).isEqualTo(409);
    assertThat(repeat.status()).isEqualTo(201);
    assertThat(repeat.replayed()).isFalse();
    assertThat(greetingsWithText(text)).containsExactly(repeat.greetingId());
  }

  @Test
  void aFailureRecordsNothingAndChangesNothing() {
    var key = newKey();
    var failing = "boom " + UUID.randomUUID();

    var failed = create(CUSTOMER, key, failing);
    var text = newText();
    var next = create(CUSTOMER, key, text);

    assertThat(failed.status()).isEqualTo(500);
    assertThat(greetingsWithText(failing)).isEmpty();
    assertThat(next.status()).isEqualTo(201);
    assertThat(next.replayed()).isFalse();
  }

  @Test
  void keysAreScopedPerCustomer() {
    var key = newKey();
    var other = FakeKeycloak.token("customer-43", "CUSTOMER");
    create(CUSTOMER, key, newText());
    var text = newText();

    var theirs = create(other, key, text);

    assertThat(theirs.status()).isEqualTo(201);
    assertThat(theirs.replayed()).isFalse();
    assertThat(greetingsWithText(text)).containsExactly(theirs.greetingId());
  }

  @Test
  void aServicesKeysAreScopedToItsClient() {
    var key = newKey();
    var text = newText();
    var client = Map.<String, Object>of("client_id", "orchestration");
    var first = FakeKeycloak.tokenWithClaims("service-account-1", client, "ORCHESTRATION");
    var second = FakeKeycloak.tokenWithClaims("service-account-2", client, "ORCHESTRATION");

    var original = create(first, key, text);
    var repeat = create(second, key, text);

    assertThat(repeat.replayed()).isTrue();
    assertThat(repeat.body()).isEqualTo(original.body());
  }

  @Test
  void aCommandThatRequiresAKeyRefusesARequestWithout() {
    var text = newText();

    var refused = create(CUSTOMER, null, text);

    assertThat(refused.status()).isEqualTo(400);
    assertThat(refused.reason()).isEqualTo("idempotencyKeyRequired");
    assertThat(greetingsWithText(text)).isEmpty();
  }

  @Test
  void aCommandThatTakesAnOptionalKeyActsOnEachRequestWithout() {
    var text = newText();

    var first = post("/greetings/drafts", CUSTOMER, null, text);
    var second = post("/greetings/drafts", CUSTOMER, null, newText());

    assertThat(first.status()).isEqualTo(201);
    assertThat(second.status()).isEqualTo(201);
    assertThat(second.replayed()).isFalse();
  }

  @Test
  void aCallerTheCommandRefusesIsForbiddenWithOrWithoutAKey() {
    var text = newText();

    var withoutKey = post("/greetings/guarded", CUSTOMER, null, text);
    var withKey = post("/greetings/guarded", CUSTOMER, newKey(), text);

    assertThat(withoutKey.status()).isEqualTo(403);
    assertThat(withKey.status()).isEqualTo(403);
    assertThat(greetingsWithText(text)).isEmpty();
  }

  @Test
  void anExemptRoleActsWithoutAKeyWhereOthersAreRefused() {
    var checkout = FakeKeycloak.token("checkout", "CHECKOUT");
    var orchestration = FakeKeycloak.token("orchestration", "ORCHESTRATION");
    var text = newText();

    var exempt = post("/greetings/exempting-checkout", checkout, null, text);
    var refused = post("/greetings/exempting-checkout", orchestration, null, newText());

    assertThat(exempt.status()).isEqualTo(201);
    assertThat(greetingsWithText(text)).containsExactly(exempt.greetingId());
    assertThat(refused.status()).isEqualTo(400);
    assertThat(refused.reason()).isEqualTo("idempotencyKeyRequired");
  }

  @Test
  void anExemptRolesKeyIsStillHonoured() {
    var checkout = FakeKeycloak.token("checkout", "CHECKOUT");
    var key = newKey();
    var text = newText();

    var first = post("/greetings/exempting-checkout", checkout, key, text);
    var repeat = post("/greetings/exempting-checkout", checkout, key, text);

    assertThat(repeat.replayed()).isTrue();
    assertThat(greetingsWithText(text)).containsExactly(first.greetingId());
  }

  @Test
  void aKeyOf255PrintableCharactersIsAccepted() {
    var key = "~ " + "k".repeat(253);

    assertThat(create(CUSTOMER, key, newText()).status()).isEqualTo(201);
  }

  @Test
  void aKeyThatIsTooLongIsRefused() {
    var refused = create(CUSTOMER, "k".repeat(256), newText());

    assertThat(refused.status()).isEqualTo(400);
    assertThat(refused.reason()).isEqualTo("idempotencyKeyInvalid");
  }

  @Test
  void aKeyThatIsNotPrintableAsciiIsRefused() {
    var refused = create(CUSTOMER, "key\tone", newText());

    assertThat(refused.status()).isEqualTo(400);
    assertThat(refused.reason()).isEqualTo("idempotencyKeyInvalid");
  }

  @Test
  void aMethodNoHandlerTakesKeepsItsOwnError() {
    http.patch()
        .uri("/greetings")
        .headers(h -> h.setBearerAuth(CUSTOMER))
        .exchange()
        .expectStatus()
        .isEqualTo(405);
  }

  private Response create(String token, String key, String text) {
    return post("/greetings", token, key, text);
  }

  private Response post(String path, String token, String key, String text) {
    var result =
        http.post()
            .uri(path)
            .headers(
                h -> {
                  h.setBearerAuth(token);
                  if (key != null) {
                    h.set("Idempotency-Key", key);
                  }
                })
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("text", text))
            .exchange()
            .returnResult(String.class);
    var body = result.getResponseBody();
    return new Response(
        result.getStatus().value(),
        result.getResponseHeaders(),
        body == null ? null : JSON.readTree(body));
  }

  private void deleteGreeting(String id) {
    http.delete()
        .uri("/greetings/{id}", id)
        .headers(h -> h.setBearerAuth(CUSTOMER))
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  private List<String> greetingsWithText(String text) {
    return http.get()
        .uri(u -> u.path("/greetings").queryParam("text", text).build())
        .headers(h -> h.setBearerAuth(CUSTOMER))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<List<String>>() {})
        .returnResult()
        .getResponseBody();
  }

  private static String newKey() {
    return "key-" + UUID.randomUUID();
  }

  private static String newText() {
    return "Hello " + UUID.randomUUID();
  }
}
