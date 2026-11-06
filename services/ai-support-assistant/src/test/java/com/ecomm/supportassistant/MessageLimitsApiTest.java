package com.ecomm.supportassistant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * A message is 1 to 2,000 characters, and a Conversation holds at most 20 of the Customer's; past
 * that, the Customer starts a new one. The Customer may send one more than that an hour here, so
 * their first in the new one is answered.
 */
@TestPropertySource(properties = "ecomm.support-assistant.messages-per-hour=21")
class MessageLimitsApiTest extends SupportAssistantApiTest {

  @Test
  void aMessageOf2000CharactersIsAnswered() {
    var id = started();

    send(id, "a".repeat(2_000)).expectStatus().isOk();
  }

  @Test
  void aLongerMessageIsABadRequest() {
    var id = started();

    send(id, "a".repeat(2_001))
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("invalidMessage");
    transcript(customerToken(), id).expectBody().jsonPath("$.messages").isEmpty();
  }

  @Test
  void anEmptyOrBlankMessageIsABadRequest() {
    var id = started();

    send(id, "").expectStatus().isBadRequest();
    send(id, "   ").expectStatus().isBadRequest();
    sendBody(customerToken(), id, "{}").expectStatus().isBadRequest();
  }

  @Test
  void aBodyThatIsntJsonIsABadRequest() {
    var id = started();

    sendBody(customerToken(), id, "where is my order?").expectStatus().isBadRequest();
  }

  @Test
  void the21stMessageIsRefusedAsConversationFull() {
    var id = started();
    for (var i = 0; i < 20; i++) {
      send(id, "Hello " + i).expectStatus().isOk();
    }

    send(id, "Hello again")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("conversationFull");
    transcript(customerToken(), id).expectBody().jsonPath("$.messages.length()").isEqualTo(40);
    send(started(), "Hello again").expectStatus().isOk();
  }
}
