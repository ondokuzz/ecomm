package com.ecomm.supportassistant.adapter.out.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.RequestOptions;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolChoiceAuto;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.domain.Entry;
import com.ecomm.supportassistant.domain.ToolCall;
import com.ecomm.supportassistant.domain.ToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Claude, through the Messages API (ADR 0003). A turn's every call carries the same tools and
 * system prompt, with the cache breakpoint after the system prompt, so both are read from the cache
 * after the first call; nothing that changes from call to call comes before it. Each of the
 * Customer's messages reaches Claude prefixed by the service with the day it was sent and the
 * Storefront's address, never in the system prompt. Claude's answers go back to it as it gave them,
 * their thinking included.
 *
 * <p>An answer that stops for a tool asks for lookups, one that ends its turn is the reply, and one
 * refused or cut off at {@value #MAX_TOKENS} tokens is a reply saying so. Any other stop leaves the
 * turn unanswered, as unavailable, though it doesn't count against the circuit breaker: Claude
 * answered.
 */
public class ClaudeSupportModel implements SupportModel {

  /** Opus 5.5 at effort {@code low} answers a support question in far fewer. */
  static final long MAX_TOKENS = 4_000;

  /** When Claude's safeguards refuse a request, the API tries the model it picks for the case. */
  static final String SERVER_SIDE_FALLBACK = "server-side-fallback-2026-07-01";

  static final String REFUSED =
      "I'm sorry, I can't help with that. I can answer questions about your Orders, Shipments,"
          + " Payments, returns and our Products.";

  static final String TOO_LONG =
      "I'm sorry, my answer got too long to finish. Could you ask about one thing at a time?";

  private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

  private static final TypeReference<List<Object>> JSON_ARRAY = new TypeReference<>() {};

  private static final DateTimeFormatter SENT_ON =
      DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

  private final AnthropicClient client;
  private final ProviderCalls calls;
  private final String model;
  private final String storefrontUrl;

  /**
   * @param calls guards each call to the Messages API
   * @param model the Claude model, such as {@code claude-opus-5-5}
   * @param storefrontUrl where Customers open the Storefront, which each message names
   */
  public ClaudeSupportModel(
      AnthropicClient client, ProviderCalls calls, String model, String storefrontUrl) {
    this.client = client;
    this.calls = calls;
    this.model = model;
    this.storefrontUrl = storefrontUrl;
  }

  @Override
  public Answer answer(Request request) {
    var params =
        MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            // Adaptive thinking is Opus 5.5's default, and can't be turned off; effort is set,
            // since the model's default is medium.
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
            .systemOfBetaTextBlockParams(
                List.of(
                    BetaTextBlockParam.builder()
                        .text(request.systemPrompt())
                        .cacheControl(BetaCacheControlEphemeral.builder().build())
                        .build()))
            .toolChoice(BetaToolChoiceAuto.builder().build())
            .messages(JsonValue.from(messages(request.transcript())))
            .addBeta(SERVER_SIDE_FALLBACK)
            .fallbacksDefault();
    request.tools().forEach(tool -> params.addTool(tool(tool)));
    var built = params.build();
    return answerOf(
        calls.call(
            timeout ->
                client
                    .beta()
                    .messages()
                    .create(built, RequestOptions.builder().timeout(timeout).build()),
            request.timeLeft()));
  }

  /** A tool, its input held to its schema. */
  private static BetaTool tool(ToolDefinition definition) {
    var schema = new LinkedHashMap<>(definition.inputSchema());
    var input =
        BetaTool.InputSchema.builder().properties(JsonValue.from(schema.remove("properties")));
    if (schema.remove("required") instanceof List<?> required) {
      required.forEach(name -> input.addRequired((String) name));
    }
    schema.remove("type");
    schema.forEach((key, value) -> input.putAdditionalProperty(key, JsonValue.from(value)));
    return BetaTool.builder()
        .name(definition.name())
        .description(definition.description())
        .inputSchema(input.build())
        .strict(true)
        .build();
  }

  /** The Conversation as Messages API messages, as JSON values. */
  private List<Map<String, Object>> messages(List<Entry> transcript) {
    var messages = new ArrayList<Map<String, Object>>();
    for (var entry : transcript) {
      messages.add(
          switch (entry) {
            case Entry.CustomerMessage m ->
                message("user", List.of(Map.of("type", "text", "text", prefix(m) + m.text())));
            case Entry.ToolUse u ->
                message(
                    "assistant",
                    asGiven(
                        u.providerContent(),
                        () -> u.calls().stream().map(ClaudeSupportModel::toolUse).toList()));
            case Entry.ToolResults r ->
                message("user", r.results().stream().map(ClaudeSupportModel::toolResult).toList());
            case Entry.AssistantReply r ->
                message(
                    "assistant",
                    asGiven(
                        r.providerContent(),
                        () -> List.of(Map.of("type", "text", "text", r.text()))));
          });
    }
    return messages;
  }

  /**
   * What the service says before the Customer's words: the day they wrote, and where to send them.
   */
  private String prefix(Entry.CustomerMessage message) {
    return "[Sent on %s. The Storefront is at %s.]\n\n"
        .formatted(SENT_ON.format(message.at()), storefrontUrl);
  }

  /**
   * An answer of Claude's as it gave it; or, for one another provider gave, such as the mock before
   * a key was set, rebuilt from what the Conversation keeps of it.
   */
  private static List<?> asGiven(String providerContent, Supplier<List<?>> rebuilt) {
    return providerContent != null ? parse(providerContent) : rebuilt.get();
  }

  private static Map<String, Object> message(String role, List<?> content) {
    return Map.of("role", role, "content", content);
  }

  private static Map<String, Object> toolUse(ToolCall call) {
    return Map.of("type", "tool_use", "id", call.id(), "name", call.tool(), "input", call.input());
  }

  private static Map<String, Object> toolResult(ToolResult result) {
    var block = new LinkedHashMap<String, Object>();
    block.put("type", "tool_result");
    block.put("tool_use_id", result.callId());
    block.put("content", json(result.content()));
    if (result.outcome() == ToolResult.Outcome.ERROR) {
      block.put("is_error", true);
    }
    return block;
  }

  private Answer answerOf(BetaMessage message) {
    var usage =
        new Usage(
            message.usage().inputTokens(),
            message.usage().outputTokens(),
            message.usage().cacheReadInputTokens().orElse(0L));
    var reason = message.stopReason().orElse(null);
    if (BetaStopReason.TOOL_USE.equals(reason)) {
      var calls =
          message.content().stream()
              .flatMap(block -> block.toolUse().stream())
              .map(call -> new ToolCall(call.id(), call.name(), call._input().convert(JSON_OBJECT)))
              .toList();
      return new ToolUse(calls, usage, json(message._content()));
    }
    if (BetaStopReason.END_TURN.equals(reason)) {
      var text =
          message.content().stream()
              .flatMap(block -> block.text().stream())
              .map(BetaTextBlock::text)
              .collect(Collectors.joining("\n\n"));
      return new Reply(text, usage, json(message._content()));
    }
    if (BetaStopReason.REFUSAL.equals(reason)) {
      return new Reply(REFUSED, usage);
    }
    if (BetaStopReason.MAX_TOKENS.equals(reason)) {
      return new Reply(TOO_LONG, usage);
    }
    throw new UnavailableException(
        "Claude stopped for " + reason + ", which a turn can't take", null);
  }

  private static String json(Object value) {
    try {
      return ObjectMappers.jsonMapper().writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Can't write as JSON", e);
    }
  }

  private static List<Object> parse(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, JSON_ARRAY);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Can't read a stored answer of Claude's", e);
    }
  }
}
