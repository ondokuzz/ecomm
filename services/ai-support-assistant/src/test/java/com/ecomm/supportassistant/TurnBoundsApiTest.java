package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.domain.Entry;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * A turn asks the provider at most 6 times: one that asks for lookups on and on ends with a reply
 * saying the question was too much to look up at once.
 */
@Import(GroundingApiTest.Provider.class)
class TurnBoundsApiTest extends SupportAssistantApiTest {

  @Autowired ScriptedSupportModel model;

  @BeforeEach
  void resetModel() {
    model.reset();
  }

  @Test
  void aTurnEndsAfterSixProviderCalls() {
    stubLatestOrderPaid();
    model.script(
        Stream.generate(GroundingApiTest::findOrders)
            .limit(10)
            .toArray(SupportModel.Answer[]::new));
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(String.class, text -> assertThat(text).contains("too much to look up at once"));

    assertThat(model.requests()).hasSize(6);
    // The Conversation takes the next message, with the bounded turn's reply before it.
    model.reset();
    model.script(GroundingApiTest.reply("Your latest Order is paid."));
    send(id, "Is it paid?").expectStatus().isOk();
    var transcript = model.requests().getFirst().transcript();
    assertThat(transcript.get(transcript.size() - 2))
        .isInstanceOfSatisfying(
            Entry.AssistantReply.class,
            reply -> assertThat(reply.text()).contains("too much to look up at once"));
  }

  @Test
  void aTurnThatEndsUnderItsBoundsIsAnsweredAsUsual() {
    stubOrders(okJson("{\"items\": [], \"page\": 0, \"size\": 5, \"total\": 0}"));
    model.script(
        GroundingApiTest.findOrders(),
        GroundingApiTest.findOrders(),
        GroundingApiTest.findOrders(),
        GroundingApiTest.findOrders(),
        GroundingApiTest.findOrders(),
        GroundingApiTest.reply("You have no Orders yet."));
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("You have no Orders yet.");
  }
}
