package com.ecomm.supportassistant.domain;

/** A Conversation already holds as many Customer messages as it may; a new one starts afresh. */
public class ConversationFullException extends RuntimeException {

  public ConversationFullException(String conversationId) {
    super(
        "Conversation "
            + conversationId
            + " already holds "
            + Conversation.MAX_CUSTOMER_MESSAGES
            + " messages; start a new one");
  }
}
