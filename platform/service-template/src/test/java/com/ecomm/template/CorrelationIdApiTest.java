package com.ecomm.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Every response names the Correlation ID of its request, and so does every problem detail. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({
  CorrelationIdApiTest.StaffOnlyController.class,
  CorrelationIdApiTest.FailingController.class
})
class CorrelationIdApiTest {

  private static final String HEADER = "X-Correlation-Id";

  @RestController
  static class StaffOnlyController {

    @GetMapping("/staff-only")
    @PreAuthorize("hasRole('STAFF')")
    String staffOnly() {
      return "ok";
    }
  }

  /** Errors the way a service raises them: its own handled exception, and an unexpected one. */
  @RestController
  static class FailingController {

    static class ConflictException extends RuntimeException {}

    @GetMapping("/conflict")
    String conflict() {
      throw new ConflictException();
    }

    @GetMapping("/boom")
    String boom() {
      throw new IllegalStateException("boom");
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
      return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Conflict.");
    }
  }

  @DynamicPropertySource
  static void keycloak(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
  }

  @Autowired RestTestClient http;

  @Test
  void aCorrelationIdIsGeneratedWhenTheRequestHasNone() {
    var id = ping(null);

    assertThat(id).isNotBlank();
    assertThat(UUID.fromString(id)).isNotNull();
  }

  @Test
  void eachRequestWithoutOneGetsItsOwn() {
    assertThat(ping(null)).isNotEqualTo(ping(null));
  }

  @Test
  void aValidIncomingCorrelationIdIsEchoed() {
    assertThat(ping("checkout-7f3a-42")).isEqualTo("checkout-7f3a-42");
  }

  @Test
  void aCorrelationIdOfSixtyFourCharactersIsAccepted() {
    var longest = "a".repeat(64);

    assertThat(ping(longest)).isEqualTo(longest);
  }

  @Test
  void aCorrelationIdThatIsTooLongIsReplaced() {
    var tooLong = "a".repeat(65);

    var id = ping(tooLong);

    assertThat(id).isNotEqualTo(tooLong);
    assertThat(UUID.fromString(id)).isNotNull();
  }

  @Test
  void aCorrelationIdWithBadCharactersIsReplaced() {
    var injected = "abc\"}{\"level\":\"ERROR";

    var id = ping(injected);

    assertThat(id).isNotEqualTo(injected);
    assertThat(UUID.fromString(id)).isNotNull();
  }

  @Test
  void anEmptyCorrelationIdIsReplaced() {
    assertThat(UUID.fromString(ping(""))).isNotNull();
  }

  @Test
  void problemDetailsCarryTheCorrelationId() {
    http.get()
        .uri("/no-such-resource")
        .header(HEADER, "not-found-1")
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-42", "CUSTOMER")))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .valueEquals(HEADER, "not-found-1")
        .expectBody()
        .jsonPath("$.correlationId")
        .isEqualTo("not-found-1");
  }

  @Test
  void unauthorizedProblemDetailsCarryTheCorrelationId() {
    http.get()
        .uri("/ping")
        .header(HEADER, "unauthorized-1")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HEADER, "unauthorized-1")
        .expectBody()
        .jsonPath("$.correlationId")
        .isEqualTo("unauthorized-1");
  }

  @Test
  void anInvalidTokensProblemDetailCarriesTheCorrelationId() {
    http.get()
        .uri("/ping")
        .header(HEADER, "unauthorized-2")
        .headers(h -> h.setBearerAuth("not-a-jwt"))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.correlationId")
        .isEqualTo("unauthorized-2");
  }

  @Test
  void forbiddenProblemDetailsCarryTheCorrelationId() {
    http.get()
        .uri("/staff-only")
        .header(HEADER, "forbidden-1")
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-42", "CUSTOMER")))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .valueEquals(HEADER, "forbidden-1")
        .expectBody()
        .jsonPath("$.correlationId")
        .isEqualTo("forbidden-1");
  }

  @Test
  void aServicesOwnProblemDetailsCarryTheCorrelationId() {
    expectProblemWithCorrelationId("/conflict", 409, "conflict-1");
  }

  @Test
  void unexpectedErrorProblemDetailsCarryTheCorrelationId() {
    expectProblemWithCorrelationId("/boom", 500, "boom-1");
  }

  @Test
  void aGeneratedCorrelationIdIsTheOneInTheProblemDetail() {
    var result =
        http.get()
            .uri("/ping")
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectBody(Problem.class);
    var response = result.returnResult();

    assertThat(response.getResponseBody().correlationId())
        .isEqualTo(response.getResponseHeaders().getFirst(HEADER));
  }

  record Problem(int status, String correlationId) {}

  private void expectProblemWithCorrelationId(String path, int status, String correlationId) {
    http.get()
        .uri(path)
        .header(HEADER, correlationId)
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-42", "CUSTOMER")))
        .exchange()
        .expectStatus()
        .isEqualTo(status)
        .expectBody()
        .jsonPath("$.correlationId")
        .isEqualTo(correlationId);
  }

  /** Pings as a Customer and returns the Correlation ID the response names. */
  private String ping(String correlationId) {
    return http.get()
        .uri("/ping")
        .headers(
            h -> {
              h.setBearerAuth(FakeKeycloak.token("customer-42", "CUSTOMER"));
              if (correlationId != null) {
                h.set(HEADER, correlationId);
              }
            })
        .exchange()
        .expectStatus()
        .isOk()
        .returnResult(String.class)
        .getResponseHeaders()
        .getFirst(HEADER);
  }
}
