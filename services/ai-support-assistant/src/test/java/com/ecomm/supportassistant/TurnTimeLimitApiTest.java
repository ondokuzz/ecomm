package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * A turn runs for at most 60 seconds, shortened here to 2: past that, the provider isn't asked
 * again, and the reply says the question was too much to look up at once.
 */
@Import(GroundingApiTest.Provider.class)
@TestPropertySource(properties = "ecomm.support-assistant.turn-time-limit=2s")
class TurnTimeLimitApiTest extends SupportAssistantApiTest {

  @Autowired ScriptedSupportModel model;

  @BeforeEach
  void resetModel() {
    model.reset();
  }

  @Test
  void aTurnEndsOnceItsTimeIsUp() {
    var paid = order(ORDER_ID, "PAID", "PLACED", "PAID");
    stubOrders(
        okJson("{\"items\": [%s], \"page\": 0, \"size\": 5, \"total\": 1}".formatted(paid))
            .withFixedDelay(1_200));
    model.script(
        Stream.generate(GroundingApiTest::findOrders).limit(5).toArray(SupportModel.Answer[]::new));
    var id = started();

    send(id, "Where is my order?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(String.class, text -> assertThat(text).contains("too much to look up at once"));

    // Each lookup takes 1.2 seconds, so the third call would come after the 2 are up.
    assertThat(model.requests()).hasSize(2);
  }
}
