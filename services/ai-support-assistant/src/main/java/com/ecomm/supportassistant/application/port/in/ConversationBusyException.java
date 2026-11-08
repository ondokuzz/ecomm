package com.ecomm.supportassistant.application.port.in;

/**
 * Another message's turn is running in the Conversation, which runs one at a time: the message can
 * be sent again once it is answered.
 */
public class ConversationBusyException extends RuntimeException {

  public ConversationBusyException() {
    super("Another message in this Conversation is still being answered; send it again shortly.");
  }
}
