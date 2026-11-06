package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A message sent with an {@code Idempotency-Key}, such as a retry after a dropped connection, is
 * answered once: a repeat with the same key answers the first reply, without asking the provider
 * again, adding to the Conversation or counting against the Customer's limits, marked {@code
 * Idempotent-Replayed: true}. The same key with another message is refused.
 */
@Import(GroundingApiTest.Provider.class)
class IdempotentMessageApiTest extends SupportAssistantApiTest {

  @Autowired ScriptedSupportModel model;

  @BeforeEach
  void resetModel() {
    model.reset();
  }

  private RestTestClient.ResponseSpec sendWithKey(String conversationId, String text, String key) {
    return http.post()
        .uri("/conversations/{id}/messages", conversationId)
        .headers(h -> h.setBearerAuth(customerToken()))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"text\": %s}".formatted(jsonString(text)))
        .exchange();
  }

  @Test
  void aRepeatedKeyAnswersTheFirstReplyWithoutRunningTheTurnAgain() {
    model.script(
        new SupportModel.Reply("Hello! What can I look up?", SupportModel.Usage.NONE),
        new SupportModel.Reply("A second answer", SupportModel.Usage.NONE));
    var id = started();
    var first =
        sendWithKey(id, "Hi", "key-1")
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    var repeat =
        sendWithKey(id, "Hi", "key-1")
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("Idempotent-Replayed", "true")
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertThat(repeat).isEqualTo(first).contains("Hello! What can I look up?");
    assertThat(model.requests()).hasSize(1);
    transcript(customerToken(), id).expectBody().jsonPath("$.messages.length()").isEqualTo(2);
  }

  @Test
  void aRepeatWhileTheFirstIsStillRunningIsRefusedThenAnswersTheFirstReply() throws Exception {
    // The first turn's lookup takes 1.5 seconds.
    stubOrders(
        okJson("{\"items\": [], \"page\": 0, \"size\": 5, \"total\": 0}").withFixedDelay(1_500));
    model.script(
        GroundingApiTest.findOrders(),
        new SupportModel.Reply("You have no Orders yet.", SupportModel.Usage.NONE));
    var id = started();
    var first =
        CompletableFuture.supplyAsync(
            () ->
                sendWithKey(id, "Where is my order?", "key-1")
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody());
    Thread.sleep(500);

    sendWithKey(id, "Where is my order?", "key-1")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("idempotencyKeyInFlight");

    var answered = first.get(10, TimeUnit.SECONDS);
    sendWithKey(id, "Where is my order?", "key-1")
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo(answered);
    assertThat(model.requests()).hasSize(2);
    transcript(customerToken(), id).expectBody().jsonPath("$.messages.length()").isEqualTo(2);
  }

  @Test
  void theSameKeyWithAnotherMessageIsRefused() {
    model.script(new SupportModel.Reply("Hello!", SupportModel.Usage.NONE));
    var id = started();
    sendWithKey(id, "Hi", "key-1").expectStatus().isOk();

    sendWithKey(id, "Where is my order?", "key-1")
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("idempotencyKeyReused");
    assertThat(model.requests()).hasSize(1);
  }

  @Test
  void anEmptyOrTooLongKeyIsABadRequest() {
    var id = started();

    for (var key : new String[] {"k".repeat(256), ""}) {
      sendWithKey(id, "Hi", key)
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.reason")
          .isEqualTo("idempotencyKeyInvalid");
    }
    assertThat(model.requests()).isEmpty();
  }

  @Test
  void anotherKeyRunsATurnOfItsOwn() {
    model.script(
        new SupportModel.Reply("Hello!", SupportModel.Usage.NONE),
        new SupportModel.Reply("Hello again!", SupportModel.Usage.NONE));
    var id = started();
    sendWithKey(id, "Hi", "key-1").expectStatus().isOk();

    sendWithKey(id, "Hi", "key-2")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("Hello again!");
    assertThat(model.requests()).hasSize(2);
  }

  @Test
  void aTurnThatFailedRunsAgainOnARepeat() {
    var id = started();
    // Nothing is scripted, so the provider throws.
    sendWithKey(id, "Hi", "key-1").expectStatus().is5xxServerError();
    model.script(new SupportModel.Reply("Hello!", SupportModel.Usage.NONE));

    sendWithKey(id, "Hi", "key-1")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("Hello!");
  }

  @Test
  void aRepeatIsntCountedAgainstTheCustomersLimit() {
    model.script(new SupportModel.Reply("Hello!", SupportModel.Usage.NONE));
    var id = started();
    sendWithKey(id, "Hi", "key-1").expectStatus().isOk();

    for (var i = 0; i < 25; i++) {
      sendWithKey(id, "Hi", "key-1").expectStatus().isOk();
    }
  }
}
