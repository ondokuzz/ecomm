package com.ecomm.supportassistant;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * A Customer starts a Conversation, reads its transcript and ends it. Only Customers may, Staff
 * included in those refused, and a Conversation is its Customer's alone.
 */
class ConversationApiTest extends SupportAssistantApiTest {

  @Test
  void startingAConversationAnswersItEmpty() {
    start(customerToken())
        .expectStatus()
        .isCreated()
        .expectHeader()
        .valueMatches("Location", "/conversations/[0-9a-f-]{36}")
        .expectBody()
        .jsonPath("$.id")
        .isNotEmpty()
        .jsonPath("$.messages")
        .isEmpty();
  }

  @Test
  void theTranscriptOfANewConversationIsEmpty() {
    var id = started();

    transcript(customerToken(), id)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(id)
        .jsonPath("$.messages")
        .isEmpty();
  }

  @Test
  void anotherCustomersConversationIsNotFound() {
    var id = started();
    var other = FakeKeycloak.token("customer-7", "CUSTOMER");

    transcript(other, id)
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    send(other, id, "where is my order?").expectStatus().isNotFound();
    end(other, id).expectStatus().isNotFound();
    transcript(customerToken(), id).expectStatus().isOk();
  }

  @Test
  void anUnknownConversationIsNotFound() {
    transcript(customerToken(), "00000000-0000-4000-8000-000000000000").expectStatus().isNotFound();
    transcript(customerToken(), "not-a-conversation").expectStatus().isNotFound();
  }

  @Test
  void anEndedConversationIsGone() {
    var id = started();

    end(customerToken(), id).expectStatus().isNoContent();

    transcript(customerToken(), id).expectStatus().isNotFound();
    end(customerToken(), id).expectStatus().isNotFound();
  }

  @Test
  void staffAreRefused() {
    var staff = FakeKeycloak.token("staff-1", "STAFF");

    start(staff).expectStatus().isForbidden();
    var id = started();
    transcript(staff, id).expectStatus().isForbidden();
    send(staff, id, "where is my order?").expectStatus().isForbidden();
    end(staff, id).expectStatus().isForbidden();
  }

  @Test
  void aServiceTokenIsRefused() {
    start(FakeKeycloak.token("checkout", "CHECKOUT")).expectStatus().isForbidden();
  }

  @Test
  void noTokenIsUnauthorized() {
    http.post().uri("/conversations").exchange().expectStatus().isUnauthorized();
  }
}
