package com.ecomm.supportassistant.application.port.in;

/** No Conversation has this ID, or not one of the Customer's: it never started, ended or lapsed. */
public class ConversationNotFoundException extends RuntimeException {

  public ConversationNotFoundException(String conversationId) {
    super("No Conversation " + conversationId);
  }
}
