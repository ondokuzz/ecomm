package com.ecomm.supportassistant.application;

import com.ecomm.supportassistant.application.port.in.ConversationNotFoundException;
import com.ecomm.supportassistant.application.port.in.ConversationsUseCase;
import com.ecomm.supportassistant.application.port.out.ConversationRepository;
import com.ecomm.supportassistant.application.port.out.FulfillmentShippingPort;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort;
import com.ecomm.supportassistant.application.port.out.PaymentPort;
import com.ecomm.supportassistant.application.port.out.ReturnsWarrantyPort;
import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.application.port.out.TimeSource;
import com.ecomm.supportassistant.domain.Conversation;
import com.ecomm.supportassistant.domain.Entry;
import com.ecomm.supportassistant.domain.Reference;
import com.ecomm.supportassistant.domain.ToolResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a Customer's Conversations. A turn adds the Customer's message, then asks the model and runs
 * the lookups it asks for, one answer at a time, until it replies. The reply's references are the
 * records the turn's lookups returned that its text names, so a link always points to a real record
 * of the Customer's, never to one the model made up.
 */
public class ConversationService implements ConversationsUseCase {

  private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

  private final ConversationRepository conversations;
  private final SupportModel model;
  private final SupportTools tools;
  private final TimeSource time;
  private final Duration lapsesAfter;

  /**
   * @param lapsesAfter how long a Conversation is kept after its last message
   */
  public ConversationService(
      ConversationRepository conversations,
      SupportModel model,
      OrderManagementPort orderManagement,
      FulfillmentShippingPort fulfillmentShipping,
      PaymentPort payment,
      ReturnsWarrantyPort returnsWarranty,
      TimeSource time,
      Duration lapsesAfter) {
    this.conversations = conversations;
    this.model = model;
    this.tools = new SupportTools(orderManagement, fulfillmentShipping, payment, returnsWarranty);
    this.time = time;
    this.lapsesAfter = lapsesAfter;
  }

  @Override
  public Conversation start(String customerId) {
    var conversation = Conversation.start(UUID.randomUUID().toString(), customerId, time.now());
    conversations.save(conversation, lapsesAfter);
    return conversation;
  }

  @Override
  public Entry.AssistantReply send(String customerId, String conversationId, String text) {
    var started = System.nanoTime();
    var conversation =
        conversation(customerId, conversationId).withCustomerMessage(text, time.now());
    var results = new ArrayList<ToolResult>();
    var calls = 0;
    var usage = SupportModel.Usage.NONE;
    var replied = false;
    try {
      while (true) {
        calls++;
        var answer =
            model.answer(
                new SupportModel.Request(
                    SystemPrompt.TEXT, SupportTools.DEFINITIONS, conversation.entries()));
        usage = usage.plus(answer.usage());
        switch (answer) {
          case SupportModel.ToolUse(var toolCalls, var ignored) -> {
            var turnResults = toolCalls.stream().map(tools::run).toList();
            results.addAll(turnResults);
            conversation = conversation.withToolUse(toolCalls).withToolResults(turnResults);
          }
          case SupportModel.Reply(var replyText, var ignored) -> {
            var reply =
                new Entry.AssistantReply(
                    replyText, referencesNamedIn(replyText, results), time.now());
            conversations.save(conversation.withReply(reply), lapsesAfter);
            replied = true;
            return reply;
          }
        }
      }
    } finally {
      // A turn that failed is logged too. The Correlation ID is in the MDC; the Customer's words
      // and the reply stay out.
      log.info(
          "Turn in Conversation {} {}: tools [{}], {} provider calls, tokens {} in, {} out, {}"
              + " cache read, in {} ms",
          conversationId,
          replied ? "replied" : "failed",
          results.stream()
              .map(r -> r.tool() + " " + r.outcome().name().toLowerCase(Locale.ROOT))
              .collect(Collectors.joining(", ")),
          calls,
          usage.input(),
          usage.output(),
          usage.cacheRead(),
          Duration.ofNanos(System.nanoTime() - started).toMillis());
    }
  }

  /** The records {@code results} returned that {@code text} names, each once, in order. */
  private static List<Reference> referencesNamedIn(String text, List<ToolResult> results) {
    var byId = new LinkedHashMap<String, Reference>();
    results.stream()
        .flatMap(r -> r.records().stream())
        .filter(record -> isNamedIn(text, record))
        .forEach(record -> byId.putIfAbsent(record.id(), record));
    return List.copyOf(byId.values());
  }

  /** Whether {@code text} names the record by its reference, with or without its {@code #}. */
  private static boolean isNamedIn(String text, Reference record) {
    var reference = record.reference().replaceFirst("^#", "");
    return Pattern.compile(
            "(?<![0-9A-Za-z])" + Pattern.quote(reference) + "(?![0-9A-Za-z])",
            Pattern.CASE_INSENSITIVE)
        .matcher(text)
        .find();
  }

  @Override
  public Conversation conversation(String customerId, String conversationId) {
    return conversations
        .find(conversationId)
        .filter(c -> c.customerId().equals(customerId))
        .orElseThrow(() -> new ConversationNotFoundException(conversationId));
  }

  @Override
  public void end(String customerId, String conversationId) {
    conversations.delete(conversation(customerId, conversationId).id());
  }
}
