package com.ecomm.supportassistant;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * A Conversation lapses a while after its last message: 24 hours, shortened here to 4 seconds. Each
 * message starts the wait again.
 */
@TestPropertySource(properties = "ecomm.support-assistant.conversation-lapses-after=4s")
class ConversationLapseApiTest extends SupportAssistantApiTest {

  @Test
  void aConversationLapsesAfterItsLastMessage() throws InterruptedException {
    var id = started();
    Thread.sleep(2_000);
    send(id, "Hello").expectStatus().isOk();
    Thread.sleep(2_000);

    // 4 seconds after it started, but 2 after its last message.
    transcript(customerToken(), id).expectStatus().isOk();
    await()
        .atMost(Duration.ofSeconds(6))
        .untilAsserted(() -> transcript(customerToken(), id).expectStatus().isNotFound());
    send(id, "Hello").expectStatus().isNotFound();
  }
}
