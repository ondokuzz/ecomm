package com.ecomm.supportassistant.application.port.out;

import com.ecomm.supportassistant.domain.Entry;
import com.ecomm.supportassistant.domain.ToolCall;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The model behind the Support Assistant (ADR 0005): given the system prompt, the tools it may ask
 * for and the Conversation so far, it answers either the reply or the lookups it needs first.
 */
public interface SupportModel {

  /**
   * @throws UnavailableException when the provider fails to answer, or is failing so often that it
   *     isn't asked for now
   * @throws BusyException when the service has asked the provider as often as it may for now
   * @throws OutOfTimeException when the turn's time ran out before the provider answered
   */
  Answer answer(Request request);

  /** The turn's time ran out while the provider was asked, or waited to be asked again. */
  class OutOfTimeException extends RuntimeException {

    public OutOfTimeException(Throwable cause) {
      super("The turn's time ran out before the provider answered", cause);
    }
  }

  /** The provider failed to answer, or has been failing, so it wasn't asked. */
  class UnavailableException extends RuntimeException {

    public UnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * The service has asked the provider as often as it may: it may again after {@code retryAfter}.
   */
  class BusyException extends RuntimeException {

    private final Duration retryAfter;

    public BusyException(Duration retryAfter) {
      super("The provider has been asked as often as it may; ask again in " + retryAfter);
      this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
      return retryAfter;
    }
  }

  /**
   * @param transcript the Conversation so far, oldest first, its lookups included
   * @param timeLeft what is left of the turn's time, which the provider's answer must come within
   */
  record Request(
      String systemPrompt, List<ToolDefinition> tools, List<Entry> transcript, Duration timeLeft) {}

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

  /**
   * The reply to the Customer: the turn ends.
   *
   * @param providerContent the answer as the provider gave it, its thinking included, which it is
   *     handed back as is on later calls; null for a provider that needs nothing back
   */
  record Reply(String text, Usage usage, String providerContent) implements Answer {

    public Reply(String text, Usage usage) {
      this(text, usage, null);
    }
  }

  /**
   * The lookups the model needs before it replies, whose results go back to it.
   *
   * @param providerContent as for {@link Reply}
   */
  record ToolUse(List<ToolCall> calls, Usage usage, String providerContent) implements Answer {

    public ToolUse(List<ToolCall> calls, Usage usage) {
      this(calls, usage, null);
    }
  }
}
