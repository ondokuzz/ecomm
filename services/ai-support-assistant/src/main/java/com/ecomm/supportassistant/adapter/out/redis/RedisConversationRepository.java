package com.ecomm.supportassistant.adapter.out.redis;

import com.ecomm.supportassistant.application.port.out.ConversationRepository;
import com.ecomm.supportassistant.domain.Conversation;
import com.ecomm.supportassistant.domain.Entry;
import com.ecomm.supportassistant.domain.Reference;
import com.ecomm.supportassistant.domain.ToolCall;
import com.ecomm.supportassistant.domain.ToolResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Each Conversation is one JSON string, {@code support-assistant:conversation:<ID>}, in the shared
 * Redis under the service's own prefix. It carries the time to live it is saved with, and Redis
 * drops it when that runs out.
 */
@Component
class RedisConversationRepository implements ConversationRepository {

  private static final Pattern ID = Pattern.compile("[0-9a-f-]{36}");

  private final StringRedisTemplate redis;
  private final JsonMapper json;

  RedisConversationRepository(StringRedisTemplate redis, JsonMapper json) {
    this.redis = redis;
    this.json = json;
  }

  /** A Conversation as it is stored; decoupled from the domain so the domain can change freely. */
  private record Stored(
      String id, String customerId, Instant lastMessageAt, List<StoredEntry> entries) {

    static Stored of(Conversation conversation) {
      return new Stored(
          conversation.id(),
          conversation.customerId(),
          conversation.lastMessageAt(),
          conversation.entries().stream().map(StoredEntry::of).toList());
    }

    Conversation toConversation() {
      return new Conversation(
          id, customerId, lastMessageAt, entries.stream().map(StoredEntry::toEntry).toList());
    }
  }

  /** One entry, its {@code type} saying which of the other fields it has. */
  private record StoredEntry(
      String type,
      String text,
      Instant at,
      List<Reference> references,
      List<ToolCall> calls,
      List<ToolResult> results,
      String providerContent,
      String idempotencyKey) {

    static StoredEntry of(Entry entry) {
      return switch (entry) {
        case Entry.CustomerMessage m ->
            new StoredEntry(
                "CUSTOMER_MESSAGE", m.text(), m.at(), null, null, null, null, m.idempotencyKey());
        case Entry.AssistantReply r ->
            new StoredEntry(
                "ASSISTANT_REPLY",
                r.text(),
                r.at(),
                r.references(),
                null,
                null,
                r.providerContent(),
                null);
        case Entry.ToolUse u ->
            new StoredEntry(
                "TOOL_USE", null, null, null, u.calls(), null, u.providerContent(), null);
        case Entry.ToolResults r ->
            new StoredEntry("TOOL_RESULTS", null, null, null, null, r.results(), null, null);
      };
    }

    Entry toEntry() {
      return switch (type) {
        case "CUSTOMER_MESSAGE" -> new Entry.CustomerMessage(text, at, idempotencyKey);
        case "ASSISTANT_REPLY" -> new Entry.AssistantReply(text, references, at, providerContent);
        case "TOOL_USE" -> new Entry.ToolUse(calls, providerContent);
        case "TOOL_RESULTS" -> new Entry.ToolResults(results);
        default -> throw new IllegalStateException("Unknown Conversation entry " + type);
      };
    }
  }

  @Override
  public void save(Conversation conversation, Duration timeToLive) {
    redis
        .opsForValue()
        .set(key(conversation.id()), json.writeValueAsString(Stored.of(conversation)), timeToLive);
  }

  @Override
  public Optional<Conversation> find(String conversationId) {
    if (!ID.matcher(conversationId).matches()) {
      return Optional.empty();
    }
    return Optional.ofNullable(redis.opsForValue().get(key(conversationId)))
        .map(value -> json.readValue(value, Stored.class).toConversation());
  }

  @Override
  public void delete(String conversationId) {
    redis.delete(key(conversationId));
  }

  private static String key(String conversationId) {
    return "support-assistant:conversation:" + conversationId;
  }
}
