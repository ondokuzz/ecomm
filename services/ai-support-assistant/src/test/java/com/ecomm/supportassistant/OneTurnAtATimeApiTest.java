package com.ecomm.supportassistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Conversation runs one turn at a time: a message sent while another turn in it runs, with an
 * {@code Idempotency-Key} or without, is refused 409 {@code conversationBusy}, before the provider
 * is asked, the message is added, or it counts against the Customer's limits. A repeat of the
 * running message's own key is refused {@code idempotencyKeyInFlight}, as {@link
 * IdempotentMessageApiTest} shows. The clock stands still unless a test moves it on.
 */
@Import({GroundingApiTest.Provider.class, SettableTimeSource.Clock.class})
class OneTurnAtATimeApiTest extends SupportAssistantApiTest {

  @Autowired ScriptedSupportModel model;

  @Autowired SettableTimeSource time;

  @BeforeEach
  void resetModelAndClock() {
    model.reset();
    time.reset();
  }

  /** Sends {@code text} in the background, its turn held at its first provider call. */
  private CompletableFuture<EntityExchangeResult<String>> sentAndHeld(
      String id, String text, String key, ScriptedSupportModel.Hold hold)
      throws InterruptedException {
    var sent =
        CompletableFuture.supplyAsync(
            () ->
                (key == null ? send(id, text) : sendWithKey(id, text, key))
                    .expectBody(String.class)
                    .returnResult());
    hold.awaitAsked();
    return sent;
  }

  private static int status(CompletableFuture<EntityExchangeResult<String>> sent) throws Exception {
    return sent.get(10, TimeUnit.SECONDS).getStatus().value();
  }

  private static void isBusy(RestTestClient.ResponseSpec response) {
    response
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("conversationBusy");
  }

  /** Scripts {@code count} replies. */
  private void scriptReplies(int count) {
    model.script(
        Stream.generate(() -> GroundingApiTest.reply("Hello!"))
            .limit(count)
            .toArray(SupportModel.Answer[]::new));
  }

  /** The Conversation's claim, as Redis holds it for whichever instance runs its turn. */
  private static String claimKey(String id) {
    return "support-assistant:in-flight:" + id;
  }

  private static String redis(String... command) throws Exception {
    var args = Stream.concat(Stream.of("redis-cli"), Stream.of(command)).toArray(String[]::new);
    return REDIS.execInContainer(args).getStdout().strip();
  }

  /**
   * Sends a message, sent with {@code firstKey}, and while its turn runs another, with {@code
   * secondKey}: the other is refused as busy, and once the first is answered a new message is.
   */
  private void anotherMessageIsRefusedAsBusy(String firstKey, String secondKey) throws Exception {
    model.script(
        GroundingApiTest.reply("Hello! What can I look up?"),
        GroundingApiTest.reply("Your Order is paid."));
    var id = started();
    var hold = model.holdNext();
    var first = sentAndHeld(id, "Hi", firstKey, hold);

    isBusy(
        secondKey == null
            ? send(id, "Where is my order?")
            : sendWithKey(id, "Where is my order?", secondKey));

    hold.release();
    assertThat(status(first)).isEqualTo(200);
    assertThat(model.requests()).hasSize(1);
    transcript(customerToken(), id)
        .expectBody()
        .jsonPath("$.messages.length()")
        .isEqualTo(2)
        .jsonPath("$.messages[0].text")
        .isEqualTo("Hi")
        .jsonPath("$.messages[1].text")
        .isEqualTo("Hello! What can I look up?");
    send(id, "Where is my order?").expectBody().jsonPath("$.text").isEqualTo("Your Order is paid.");
  }

  @Test
  void anotherMessageWhileATurnRunsIsRefusedAsBusy() throws Exception {
    anotherMessageIsRefusedAsBusy("key-1", "key-2");
  }

  @Test
  void aMessageWithoutAKeyWhileOneWithoutAKeyRunsIsRefusedAsBusy() throws Exception {
    anotherMessageIsRefusedAsBusy(null, null);
  }

  @Test
  void anAnsweredKeyReplaysItsReplyWhileAnotherMessagesTurnRuns() throws Exception {
    model.script(GroundingApiTest.reply("Hello!"), GroundingApiTest.reply("Your Order is paid."));
    var id = started();
    sendWithKey(id, "Hi", "key-1").expectStatus().isOk();
    var hold = model.holdNext();
    var second = sentAndHeld(id, "Where is my order?", null, hold);

    sendWithKey(id, "Hi", "key-1")
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Idempotent-Replayed", "true")
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("Hello!");

    hold.release();
    assertThat(status(second)).isEqualTo(200);
  }

  @Test
  void aBusyMessageIsntCountedAgainstTheCustomersLimit() throws Exception {
    // 20 an hour: the held one, and 19 more, answered only if the busy one didn't count.
    scriptReplies(20);
    var id = started();
    var hold = model.holdNext();
    var first = sentAndHeld(id, "Hi", null, hold);
    isBusy(send(id, "Where is my order?"));
    hold.release();
    assertThat(status(first)).isEqualTo(200);

    for (var i = 0; i < 19; i++) {
      send(id, "Hello " + i).expectStatus().isOk();
    }
  }

  @Test
  void aConversationAt19MessagesWithTwoSentAtOnceEndsAt20() throws Exception {
    scriptReplies(20);
    var id = started();
    for (var i = 0; i < 19; i++) {
      send(id, "Hello " + i).expectStatus().isOk();
    }
    var hold = model.holdNext();
    var twentieth = sentAndHeld(id, "The 20th", null, hold);

    isBusy(send(id, "The 21st"));

    hold.release();
    assertThat(status(twentieth)).isEqualTo(200);
    transcript(customerToken(), id)
        .expectBody()
        .jsonPath("$.messages.length()")
        .isEqualTo(40)
        .jsonPath("$.messages[38].text")
        .isEqualTo("The 20th");
    send(id, "The 21st")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("conversationFull");
  }

  @Test
  void anotherConversationOfTheCustomersIsAnsweredWhileATurnRuns() throws Exception {
    scriptReplies(2);
    var id = started();
    var other = started();
    var hold = model.holdNext();
    var first = sentAndHeld(id, "Hi", null, hold);

    send(other, "Where is my order?").expectStatus().isOk();

    hold.release();
    assertThat(status(first)).isEqualTo(200);
  }

  @Test
  void aTurnTheProviderFailedReleasesItsConversation() {
    var id = started();
    model.fail(new SupportModel.UnavailableException("Down", null));
    model.fail(new SupportModel.BusyException(Duration.ofSeconds(5)));
    model.script(GroundingApiTest.reply("Hello!"));

    send(id, "Hi")
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("assistantUnavailable");
    send(id, "Hi")
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("assistantBusy");
    send(id, "Hi").expectStatus().isOk();
  }

  @Test
  void aMessagePastTheCustomersLimitReleasesItsConversation() {
    scriptReplies(21);
    var full = started();
    for (var i = 0; i < 20; i++) {
      send(full, "Hello " + i).expectStatus().isOk();
    }
    var id = started();
    send(id, "Hi").expectStatus().isEqualTo(429);

    time.advance(Duration.ofHours(1));
    send(id, "Hi").expectStatus().isOk();
  }

  @Test
  void aClaimLapsesAfterTheTurnLimitAndAMinute() throws Exception {
    model.script(GroundingApiTest.reply("Hello!"));
    var id = started();
    var hold = model.holdNext();
    var first = sentAndHeld(id, "Hi", null, hold);

    assertThat(Long.parseLong(redis("PTTL", claimKey(id)))).isBetween(119_000L, 120_000L);

    hold.release();
    assertThat(status(first)).isEqualTo(200);
  }

  @Test
  void aClaimLeftByAnInstanceThatStoppedLapsesAndTheNextMessageIsAnswered() throws Exception {
    model.script(GroundingApiTest.reply("Hello!"));
    var id = started();
    redis("SET", claimKey(id), "a-stopped-instance's-turn", "PX", "500");

    isBusy(send(id, "Hi"));

    Thread.sleep(600);
    send(id, "Hi").expectStatus().isOk();
  }

  @Test
  void aTurnWhoseClaimLapsedDoesntReleaseTheNextOnes() throws Exception {
    model.script(GroundingApiTest.reply("Hello!"));
    var id = started();
    var hold = model.holdNext();
    var first = sentAndHeld(id, "Hi", null, hold);
    // As if the first's claim lapsed and another instance's turn claimed the Conversation.
    redis("SET", claimKey(id), "another-instance's-turn", "PX", "60000");

    hold.release();
    assertThat(status(first)).isEqualTo(200);

    isBusy(send(id, "Where is my order?"));
  }
}
