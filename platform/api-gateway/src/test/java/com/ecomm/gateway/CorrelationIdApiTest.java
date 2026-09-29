package com.ecomm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * The gateway is where a request's Correlation ID is usually born. The service it forwards to and
 * the browser it answers see the same one, and so does its access log.
 */
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdApiTest extends GatewayApiTest {

  @Test
  void aValidIncomingCorrelationIdIsKept() {
    var id = correlationIdAnswering("storefront-7f3a-42");

    assertThat(id).isEqualTo("storefront-7f3a-42");
    assertThat(forwardedCorrelationId()).isEqualTo("storefront-7f3a-42");
  }

  @Test
  void aMalformedIncomingCorrelationIdIsReplacedOnBothSides() {
    var injected = "abc\"}{\"level\":\"ERROR";

    var id = correlationIdAnswering(injected);

    assertThat(UUID.fromString(id)).isNotNull();
    assertThat(forwardedCorrelationId()).isEqualTo(id);
  }

  @Test
  void aCorrelationIdIsBornWhenTheRequestHasNone() {
    var id = correlationIdAnswering(null);

    assertThat(UUID.fromString(id)).isNotNull();
    assertThat(forwardedCorrelationId()).isEqualTo(id);
  }

  @Test
  void theResponseNamesItOnceEvenWhenTheServiceEchoesIt() {
    DOWNSTREAM.stubFor(
        any(anyUrl()).willReturn(ok().withHeader(CORRELATION_ID, "storefront-7f3a-42")));

    var values =
        http.get()
            .uri("/api/catalog/products")
            .header(CORRELATION_ID, "storefront-7f3a-42")
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(String.class)
            .getResponseHeaders()
            .get(CORRELATION_ID);

    assertThat(values).containsExactly("storefront-7f3a-42");
  }

  @Test
  void aRejectedRequestsProblemDetailCarriesIt() {
    http.get()
        .uri("/api/cart/cart")
        .header(CORRELATION_ID, "unauthorized-1")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(CORRELATION_ID, "unauthorized-1")
        .expectBody()
        .jsonPath("$.correlationId")
        .isEqualTo("unauthorized-1");
  }

  @Test
  void eachRequestLeavesOneAccessLogLine(CapturedOutput output) {
    correlationIdAnswering("access-log-1");
    http.get().uri("/api/cart/cart").header(CORRELATION_ID, "access-log-2").exchange();

    assertThat(output)
        .containsOnlyOnce("GET /api/catalog/products -> 200 in ")
        .containsOnlyOnce("GET /api/cart/cart -> 401 in ");
    assertThat(output.getOut().lines().filter(line -> line.contains("GET /api/catalog/products")))
        .singleElement()
        .asString()
        .contains("access-log-1");
  }

  /** Reads a public route and returns the Correlation ID the response names. */
  private String correlationIdAnswering(String correlationId) {
    return http.get()
        .uri("/api/catalog/products")
        .headers(
            h -> {
              if (correlationId != null) {
                h.set(CORRELATION_ID, correlationId);
              }
            })
        .exchange()
        .expectStatus()
        .isOk()
        .returnResult(String.class)
        .getResponseHeaders()
        .getFirst(CORRELATION_ID);
  }

  private static String forwardedCorrelationId() {
    assertThat(forwarded()).singleElement();
    return forwarded().getFirst().getHeader(CORRELATION_ID);
  }
}
