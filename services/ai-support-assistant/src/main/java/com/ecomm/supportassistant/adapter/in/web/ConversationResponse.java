package com.ecomm.supportassistant.adapter.in.web;

import com.ecomm.supportassistant.domain.Conversation;
import java.util.List;

/** A Conversation's transcript, oldest first; its lookups are kept for the model, not shown. */
record ConversationResponse(String id, List<MessageResponse> messages) {

  static ConversationResponse of(Conversation conversation) {
    return new ConversationResponse(
        conversation.id(), conversation.messages().stream().map(MessageResponse::of).toList());
  }
}
