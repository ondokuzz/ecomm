package com.ecomm.supportassistant.adapter.in.web;

import com.ecomm.supportassistant.domain.Entry;
import com.ecomm.supportassistant.domain.Reference;
import java.time.Instant;
import java.util.List;

/**
 * One message of a Conversation: the Customer's ({@code CUSTOMER}, without references) or the
 * assistant's ({@code ASSISTANT}).
 */
record MessageResponse(String role, String text, List<ReferenceResponse> references, Instant at) {

  static MessageResponse of(Entry.Message message) {
    return switch (message) {
      case Entry.CustomerMessage m -> new MessageResponse("CUSTOMER", m.text(), List.of(), m.at());
      case Entry.AssistantReply r ->
          new MessageResponse(
              "ASSISTANT",
              r.text(),
              r.references().stream().map(ReferenceResponse::of).toList(),
              r.at());
    };
  }

  /** A record the reply names, which the Storefront links to its page by kind and ID. */
  record ReferenceResponse(String kind, String id, String reference, String label) {

    static ReferenceResponse of(Reference reference) {
      return new ReferenceResponse(
          reference.kind().name(), reference.id(), reference.reference(), reference.label());
    }
  }
}
