package com.ecomm.supportassistant.domain;

import java.time.Instant;
import java.util.List;

/** One step of a Conversation: a message, or a turn's lookups. */
public sealed interface Entry {

  /** What the Customer reads in the transcript. */
  sealed interface Message extends Entry {
    String text();

    Instant at();
  }

  record CustomerMessage(String text, Instant at) implements Message {}

  /**
   * The assistant's reply, with the records it names that the turn's lookups returned, for the
   * Storefront to link.
   */
  record AssistantReply(String text, List<Reference> references, Instant at) implements Message {
    public AssistantReply {
      references = List.copyOf(references);
    }
  }

  /** The lookups the model asked for in one answer. */
  record ToolUse(List<ToolCall> calls) implements Entry {
    public ToolUse {
      calls = List.copyOf(calls);
    }
  }

  /** What those lookups found, one result per call. */
  record ToolResults(List<ToolResult> results) implements Entry {
    public ToolResults {
      results = List.copyOf(results);
    }
  }
}
