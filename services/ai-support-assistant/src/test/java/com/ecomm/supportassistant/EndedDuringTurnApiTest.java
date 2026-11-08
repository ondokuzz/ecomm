package com.ecomm.supportassistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * A Conversation ended while one of its turns runs stays ended: the turn saves nothing, and its
 * message answers 404, as for any ended Conversation. That a turn whose Conversation is still kept
 * keeps it for another full lapse, {@link ConversationLapseApiTest} shows.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import(GroundingApiTest.Provider.class)
class EndedDuringTurnApiTest extends SupportAssistantApiTest {

  @Autowired ScriptedSupportModel model;

  @BeforeEach
  void resetModel() {
    model.reset();
  }

  /** Sends {@code text} in the background, its turn held at its first provider call. */
  private CompletableFuture<EntityExchangeResult<String>> sentAndHeld(
      String id, String text, ScriptedSupportModel.Hold hold) throws InterruptedException {
    var sent =
        CompletableFuture.supplyAsync(() -> send(id, text).expectBody(String.class).returnResult());
    hold.awaitAsked();
    return sent;
  }

  private double goneTurns() {
    return value(scrape(), "support_assistant_turn_duration_seconds_count", "outcome=\"gone\"");
  }

  @Test
  void aTurnWhoseConversationWasEndedMeanwhileAnswersNotFoundAndSavesNothing() throws Exception {
    model.script(GroundingApiTest.reply("Hello! What can I look up?"));
    var id = started();
    var hold = model.holdNext();
    var sent = sentAndHeld(id, "Hi", hold);

    end(customerToken(), id).expectStatus().isNoContent();
    hold.release();

    var answered = sent.get(10, TimeUnit.SECONDS);
    assertThat(answered.getStatus().value()).isEqualTo(404);
    assertThat(answered.getResponseBody()).contains("\"No Conversation " + id + "\"");
    transcript(customerToken(), id).expectStatus().isNotFound();
  }

  @Test
  void aBoundedTurnWhoseConversationWasEndedMeanwhileAnswersNotFoundAndSavesNothing()
      throws Exception {
    stubOrders();
    model.script(
        Stream.generate(GroundingApiTest::findOrders).limit(6).toArray(SupportModel.Answer[]::new));
    var id = started();
    var hold = model.holdNext();
    var sent = sentAndHeld(id, "Where is my order?", hold);

    end(customerToken(), id).expectStatus().isNoContent();
    hold.release();

    assertThat(sent.get(10, TimeUnit.SECONDS).getStatus().value()).isEqualTo(404);
    assertThat(model.requests()).hasSize(6);
    transcript(customerToken(), id).expectStatus().isNotFound();
  }

  @Test
  void aTurnWhoseConversationWentIsLoggedAndCountedAsGone(CapturedOutput output) throws Exception {
    var before = goneTurns();
    model.script(GroundingApiTest.reply("Your Order for Erika is paid."));
    var id = started();
    var hold = model.holdNext();
    var sent = sentAndHeld(id, "Where is my order, the one for Erika?", hold);
    end(customerToken(), id).expectStatus().isNoContent();
    hold.release();
    sent.get(10, TimeUnit.SECONDS);

    assertThat(output.getOut().lines().filter(l -> l.contains("Turn in Conversation " + id)))
        .singleElement()
        .satisfies(
            l ->
                assertThat(l)
                    .contains("Turn in Conversation " + id + " gone:")
                    .contains("[test-correlation-1]")
                    .contains("1 provider calls"));
    assertThat(output.getOut()).doesNotContain("Erika").doesNotContain("is paid");
    assertThat(goneTurns()).isEqualTo(before + 1);
  }
}
