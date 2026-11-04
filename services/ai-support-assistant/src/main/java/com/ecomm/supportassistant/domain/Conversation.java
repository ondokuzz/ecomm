package com.ecomm.supportassistant.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A Customer's exchange with the Support Assistant: their messages, the assistant's replies, and
 * the turns' lookups in between, oldest first. The lookups are kept so a follow-up is grounded in
 * what was looked up before.
 *
 * @param lastMessageAt when the last message was added, or the Conversation started; it lapses a
 *     day after
 */
public record Conversation(
    String id, String customerId, Instant lastMessageAt, List<Entry> entries) {

  /** How many messages a Customer may send in one Conversation. */
  public static final int MAX_CUSTOMER_MESSAGES = 20;

  /** How long a Customer's message may be, in characters. */
  public static final int MAX_MESSAGE_LENGTH = 2_000;

  public Conversation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(customerId, "customerId");
    Objects.requireNonNull(lastMessageAt, "lastMessageAt");
    entries = List.copyOf(entries);
  }

  public static Conversation start(String id, String customerId, Instant at) {
    return new Conversation(id, customerId, at, List.of());
  }

  /**
   * The Conversation with the Customer's {@code message} added. A message is 1 to {@value
   * #MAX_MESSAGE_LENGTH} characters, not only whitespace; a Conversation holds at most {@value
   * #MAX_CUSTOMER_MESSAGES} of them.
   */
  public Conversation withCustomerMessage(String text, Instant at) {
    if (text == null || text.isBlank() || text.length() > MAX_MESSAGE_LENGTH) {
      throw new InvalidMessageException("A message is 1 to " + MAX_MESSAGE_LENGTH + " characters");
    }
    if (customerMessages() >= MAX_CUSTOMER_MESSAGES) {
      throw new ConversationFullException(id);
    }
    return with(new Entry.CustomerMessage(text, at), at);
  }

  public Conversation withToolUse(List<ToolCall> calls) {
    return with(new Entry.ToolUse(calls), lastMessageAt);
  }

  public Conversation withToolResults(List<ToolResult> results) {
    return with(new Entry.ToolResults(results), lastMessageAt);
  }

  public Conversation withReply(Entry.AssistantReply reply) {
    return with(reply, reply.at());
  }

  /** The messages a Customer reads: theirs and the assistant's replies, oldest first. */
  public List<Entry.Message> messages() {
    return entries.stream()
        .filter(Entry.Message.class::isInstance)
        .map(Entry.Message.class::cast)
        .toList();
  }

  private long customerMessages() {
    return entries.stream().filter(Entry.CustomerMessage.class::isInstance).count();
  }

  private Conversation with(Entry entry, Instant at) {
    var next = new ArrayList<>(entries);
    next.add(entry);
    return new Conversation(id, customerId, at, next);
  }
}
