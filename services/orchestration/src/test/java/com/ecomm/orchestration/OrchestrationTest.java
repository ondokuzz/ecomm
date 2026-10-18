package com.ecomm.orchestration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.ecomm.commons.security.FakeKeycloak;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.protobuf.util.Durations;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.api.testservice.v1.SleepRequest;
import io.temporal.serviceclient.TestServiceStubs;
import io.temporal.serviceclient.TestServiceStubsOptions;
import io.temporal.testserver.TestServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for Orchestration's tests. The application runs against Temporal's own test server, reached
 * over gRPC like the real one, with time skipping on: whenever every workflow is only waiting, on a
 * retry's backoff or a timer, the server's clock jumps ahead. One WireMock server stands in for
 * every service Orchestration calls and for Keycloak's token endpoint. Each test starts with no
 * stubs. The {@link Kafka} broker carries the topics Orchestration consumes, with its consumer
 * groups' dead-letter and redrive topics.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class OrchestrationTest {

  /** How long the Saga awaits a pending authorization here. */
  static final String SETTLEMENT_DEADLINE = "2m";

  /**
   * How long the fulfillment Saga awaits delivery here: far shorter than the stack's 30 days, so a
   * test shows the setting is the one honoured. The fulfillment tests lock time skipping, so it
   * passes in real time.
   */
  static final java.time.Duration DELIVERY_DEADLINE = java.time.Duration.ofSeconds(10);

  /**
   * How long the fulfillment Saga awaits packing before it reads the Shipment again here: far
   * shorter than the stack's hour, and still longer than a test takes to send its PACKED event.
   */
  static final java.time.Duration PACKING_CHECK = java.time.Duration.ofSeconds(8);

  /**
   * How long a call to another service may take here: shorter than the stack's 25 seconds, so a
   * test can outlast it without waiting as long, and still more than a cold service's first answer.
   */
  static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

  static final String TOKEN_PATH = "/realms/ecomm/protocol/openid-connect/token";

  static final int TEMPORAL_PORT = freePort();

  static final WireMockServer DOWNSTREAM = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    TestServer.createPortBoundServer(TEMPORAL_PORT, false);
    DOWNSTREAM.start();
    for (var topic : FulfillmentEvents.TOPICS) {
      Kafka.createTopic(topic);
    }
    for (var group : FulfillmentEvents.GROUPS) {
      Kafka.createDeadLetterTopicsOf(group);
    }
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    registry.add("spring.kafka.bootstrap-servers", Kafka::bootstrapServers);
    registry.add("spring.temporal.connection.target", () -> "localhost:" + TEMPORAL_PORT);
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri",
        () -> DOWNSTREAM.baseUrl() + TOKEN_PATH);
    for (var service :
        List.of(
            "order-management",
            "payment",
            "inventory",
            "cart",
            "checkout-pricing",
            "fulfillment-shipping")) {
      registry.add("ecomm.orchestration." + service + "-url", DOWNSTREAM::baseUrl);
    }
    registry.add(
        "spring.security.oauth2.client.registration.orchestration.client-secret", () -> "test");
    // Shorter than the stack's 10 minutes, so a test shows the setting is the one honoured.
    registry.add("ecomm.orchestration.settlement-deadline", () -> SETTLEMENT_DEADLINE);
    registry.add("ecomm.orchestration.delivery-deadline", DELIVERY_DEADLINE::toString);
    registry.add("ecomm.orchestration.packing-check", PACKING_CHECK::toString);
    registry.add("ecomm.orchestration.read-timeout", READ_TIMEOUT::toString);
  }

  /**
   * Fails a class at once, rather than letting it hang, when the test server has stopped skipping
   * time: an hour of the server's time must pass in moments.
   */
  @BeforeAll
  static void timeSkips() {
    var server =
        TestServiceStubs.newServiceStubs(
            TestServiceStubsOptions.newBuilder()
                .setTarget("localhost:" + TEMPORAL_PORT)
                .validateAndBuildWithDefaults());
    try {
      server
          .blockingStub()
          .withDeadlineAfter(15, TimeUnit.SECONDS)
          .sleep(SleepRequest.newBuilder().setDuration(Durations.fromHours(1)).build());
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
        throw new AssertionError("The test server no longer skips time", e);
      }
      throw e;
    } finally {
      server.shutdownNow();
    }
  }

  @BeforeEach
  void resetDownstream() {
    DOWNSTREAM.resetAll();
  }

  static int freePort() {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
