package com.ecomm.supportassistant.application.port.in;

/**
 * The provider failed to answer, or has been failing, so the turn couldn't be answered. The
 * Conversation is as it was before the message.
 */
public class AssistantUnavailableException extends RuntimeException {

  public AssistantUnavailableException(Throwable cause) {
    super("The Support Assistant is unavailable; try again later", cause);
  }
}
