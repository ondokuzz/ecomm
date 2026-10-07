package com.ecomm.orchestration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.ecomm.commons.security.FakeKeycloak;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.temporal.testserver.TestServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for Orchestration's tests. The application runs against Temporal's own test server, reached
 * over gRPC like the real one, and one WireMock server stands in for every service it calls and for
 * Keycloak's token endpoint. Each test starts with no stubs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class OrchestrationTest {

  static final String TOKEN_PATH = "/realms/ecomm/protocol/openid-connect/token";

  private static final int TEMPORAL_PORT = freePort();

  static final WireMockServer DOWNSTREAM = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    TestServer.createPortBoundServer(TEMPORAL_PORT);
    DOWNSTREAM.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    registry.add("spring.temporal.connection.target", () -> "localhost:" + TEMPORAL_PORT);
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri",
        () -> DOWNSTREAM.baseUrl() + TOKEN_PATH);
    registry.add(
        "spring.security.oauth2.client.registration.orchestration.client-secret", () -> "test");
  }

  @BeforeEach
  void resetDownstream() {
    DOWNSTREAM.resetAll();
  }

  private static int freePort() {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
