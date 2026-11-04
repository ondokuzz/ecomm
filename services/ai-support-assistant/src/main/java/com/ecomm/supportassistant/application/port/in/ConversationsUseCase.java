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
   * replies.
   */
  Entry.AssistantReply send(String customerId, String conversationId, String text);

  Conversation conversation(String customerId, String conversationId);

  void end(String customerId, String conversationId);
}
