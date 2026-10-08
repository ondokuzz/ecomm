package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * {@code payment.gateway} picks the gateway adapter; one Payment doesn't have fails at startup. A
 * service that never starts has no HTTP seam to test at, so this one starts the app itself.
 */
class GatewaySelectionTest {

  @Test
  void anUnknownGatewayFailsAtStartup() {
    var properties = new HashMap<String, Object>(infrastructure());
    properties.put("payment.gateway", "acme");
    // As command-line arguments, which outrank application.yml; the builder's properties don't.
    var args =
        properties.entrySet().stream()
            .map(p -> "--" + p.getKey() + "=" + p.getValue())
            .toArray(String[]::new);

    assertThatThrownBy(
            () -> new SpringApplicationBuilder(PaymentApplication.class).run(args).close())
        .hasStackTraceContaining("payment.gateway");
  }

  /** Everything else the app needs to start, so that only the gateway can stop it. */
  private static Map<String, Object> infrastructure() {
    var properties = new HashMap<String, Object>();
    FakeKeycloak.registerWith((name, value) -> properties.put(name, value.get()));
    EventBackbone.registerWith((name, value) -> properties.put(name, value.get()));
    PaymentApiTest.POSTGRES.start();
    properties.put("spring.datasource.url", PaymentApiTest.POSTGRES.getJdbcUrl());
    properties.put("spring.datasource.username", PaymentApiTest.POSTGRES.getUsername());
    properties.put("spring.datasource.password", PaymentApiTest.POSTGRES.getPassword());
    properties.put("spring.flyway.locations", "classpath:db/migration,classpath:db/testdata");
    properties.put("server.port", 0);
    return properties;
  }
}
