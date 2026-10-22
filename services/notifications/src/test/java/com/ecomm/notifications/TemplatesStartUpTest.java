package com.ecomm.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * The service refuses to start when a Message type lacks a template for a channel, or an SMS
 * rendered with its sample doesn't fit 160 characters: each an application context of its own,
 * whose templates have {@code ORDER_CONFIRMED}'s email without its text part, or {@code
 * ORDER_SHIPPED}'s SMS too long.
 */
class TemplatesStartUpTest {

  @Test
  void aMissingTemplateStopsTheServiceStarting() {
    assertThatStartingFails(
        "classpath:/incomplete-templates/", "order-confirmed/email/en/text.mustache is missing");
  }

  @Test
  void anSmsOver160CharactersStopsTheServiceStarting() {
    assertThatStartingFails(
        "classpath:/long-sms-templates/",
        "order-shipped/sms/en/text.mustache renders its sample in");
  }

  private static void assertThatStartingFails(String templates, String problem) {
    var properties = new HashMap<String, Object>();
    DynamicPropertyRegistry registry = (name, value) -> properties.put(name, value);
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    Keycloak.registerWith(registry);
    registry.add("spring.datasource.url", NotificationsApiTest.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", NotificationsApiTest.POSTGRES::getUsername);
    registry.add("spring.datasource.password", NotificationsApiTest.POSTGRES::getPassword);
    registry.add("server.port", () -> "0");
    registry.add("ecomm.notifications.dispatch.enabled", () -> "false");
    registry.add("spring.kafka.listener.auto-startup", () -> "false");
    registry.add("ecomm.notifications.templates.location", () -> templates);

    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(NotificationsApplication.class)
                    .run(arguments(properties))
                    .close())
        .satisfies(
            e ->
                assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                    .contains(problem));
  }

  /** As command-line arguments, which outrank {@code application.yml}. */
  private static String[] arguments(Map<String, Object> properties) {
    return properties.entrySet().stream()
        .map(
            e ->
                "--"
                    + e.getKey()
                    + "="
                    + (e.getValue() instanceof Supplier<?> s ? s.get() : e.getValue()))
        .toArray(String[]::new);
  }
}
