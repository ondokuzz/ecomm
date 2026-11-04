package com.ecomm.supportassistant.application.port.out;

import com.ecomm.supportassistant.domain.Entry;
import com.ecomm.supportassistant.domain.ToolCall;
import java.util.List;
import java.util.Map;

/**
 * The model behind the Support Assistant (ADR 0005): given the system prompt, the tools it may ask
 * for and the Conversation so far, it answers either the reply or the lookups it needs first.
 */
public interface SupportModel {

  Answer answer(Request request);

  /**
   * @param transcript the Conversation so far, oldest first, its lookups included
   */
  record Request(String systemPrompt, List<ToolDefinition> tools, List<Entry> transcript) {}

  /**
   * A tool the model may ask for.
   *
   * @param inputSchema the JSON Schema of its input, as JSON values
   */
  record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {}

  /** The tokens an answer took. */
  record Usage(long input, long output, long cacheRead) {

    public static final Usage NONE = new Usage(0, 0, 0);

    public Usage plus(Usage other) {
      return new Usage(input + other.input, output + other.output, cacheRead + other.cacheRead);
    }
  }

  sealed interface Answer {
    Usage usage();
  }

  /** The reply to the Customer: the turn ends. */
  record Reply(String text, Usage usage) implements Answer {}

  /** The lookups the model needs before it replies, whose results go back to it. */
  record ToolUse(List<ToolCall> calls, Usage usage) implements Answer {}
}
