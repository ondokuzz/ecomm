package com.ecomm.supportassistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * What the assistant costs and how it fails, on {@code /actuator/prometheus} under {@code
 * support_assistant_}: the tokens by kind, the turns' durations by outcome, the refusals by limit,
 * and the provider's errors by kind and the circuit breaker's state. Each counter is there at 0
 * from the start, so Grafana's {@code increase()} counts the first one. The Customer may send 2
 * messages an hour here.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Import(GroundingApiTest.Provider.class)
@TestPropertySource(properties = "ecomm.support-assistant.messages-per-hour=2")
class AssistantMetricsApiTest extends SupportAssistantApiTest {

  @Autowired ScriptedSupportModel model;

  @BeforeEach
  void resetModel() {
    model.reset();
  }

  @Test
  void theAssistantsCostAndFailuresAreCountedFromZero() {
    var before = scrape();
    for (var kind : new String[] {"input", "output", "cache_read"}) {
      assertThat(value(before, "support_assistant_tokens_total", "kind=\"" + kind + "\"")).isZero();
    }
    for (var limit :
        new String[] {"messages_per_hour", "messages_per_day", "provider_calls_per_minute"}) {
      assertThat(value(before, "support_assistant_limit_refusals_total", "limit=\"" + limit + "\""))
          .isZero();
    }
    for (var kind : new String[] {"rate_limited", "overloaded", "server_error", "timeout"}) {
      assertThat(value(before, "support_assistant_provider_errors_total", "kind=\"" + kind + "\""))
          .isZero();
    }
    assertThat(value(before, "support_assistant_provider_breaker_state", "state=\"closed\""))
        .isEqualTo(1);
    assertThat(value(before, "support_assistant_provider_breaker_state", "state=\"open\""))
        .isZero();

    model.script(
        new SupportModel.ToolUse(
            GroundingApiTest.findOrders().calls(), new SupportModel.Usage(3000, 40, 0)),
        new SupportModel.Reply("You have no Orders yet.", new SupportModel.Usage(400, 20, 2900)));
    stubOrders();
    var id = started();
    send(id, "Where is my order?").expectStatus().isOk();
    // Nothing is scripted, so the provider throws.
    send(id, "Hello").expectStatus().is5xxServerError();
    send(id, "Hello").expectStatus().isEqualTo(429);

    var after = scrape();
    assertThat(value(after, "support_assistant_tokens_total", "kind=\"input\"")).isEqualTo(3400);
    assertThat(value(after, "support_assistant_tokens_total", "kind=\"output\"")).isEqualTo(60);
    assertThat(value(after, "support_assistant_tokens_total", "kind=\"cache_read\""))
        .isEqualTo(2900);
    assertThat(value(after, "support_assistant_turn_duration_seconds_count", "outcome=\"replied\""))
        .isEqualTo(1);
    assertThat(value(after, "support_assistant_turn_duration_seconds_count", "outcome=\"failed\""))
        .isEqualTo(1);
    assertThat(
            value(after, "support_assistant_limit_refusals_total", "limit=\"messages_per_hour\""))
        .isEqualTo(1);
  }
}
