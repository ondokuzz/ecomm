package com.ecomm.supportassistant.application.port.in;

import com.ecomm.supportassistant.domain.Conversation;
import com.ecomm.supportassistant.domain.Entry;

/**
 * A Customer's Conversations with the Support Assistant. Each is its Customer's alone: another
 * Customer's, or one that lapsed, is {@link ConversationNotFoundException}.
 */
public interface ConversationsUseCase {

  Conversation start(String customerId);

  /**
   * Adds the Customer's message and runs one turn: the model and the lookups it asks for, until it
   * replies. A message sent again with the same {@code idempotencyKey} is answered the first one's
   * reply, with no turn run.
   *
   * @param idempotencyKey null for none
   */
  Sent send(String customerId, String conversationId, String text, String idempotencyKey);

  /**
   * @param replayed whether it is the reply to an earlier message with the same key
   */
  record Sent(Entry.AssistantReply reply, boolean replayed) {}

  Conversation conversation(String customerId, String conversationId);

  void end(String customerId, String conversationId);
}
