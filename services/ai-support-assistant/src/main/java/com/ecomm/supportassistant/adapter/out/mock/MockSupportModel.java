package com.ecomm.supportassistant.adapter.out.mock;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.domain.Entry;
import com.ecomm.supportassistant.domain.ToolCall;
import com.ecomm.supportassistant.domain.ToolResult;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The scripted provider the stack runs without an API key (ADR 0005). It is deterministic: it picks
 * its tools by keywords in the Customer's latest message, and writes its reply from a template over
 * what they found, naming each record. Asked about Orders, it finds the Customer's latest and looks
 * it up with its Order Status history.
 */
public class MockSupportModel implements SupportModel {

  private static final Pattern ABOUT_ORDERS =
      Pattern.compile(
          "\\b(orders?|where|status|deliver\\w*|parcel|package|shipp\\w*)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

  static final String FIND_ORDERS = "find_orders";
  static final String GET_ORDER = "get_order";

  static final String UNAVAILABLE =
      "I couldn't look up your Orders just now. Please try again in a moment.";

  static final String WHAT_I_DO =
      "I can help with your Orders. Ask me, for example, \"Where is my Order?\"";

  @Override
  public Answer answer(Request request) {
    var transcript = request.transcript();
    var last = transcript.getLast();
    var calls = (int) transcript.stream().filter(Entry.ToolUse.class::isInstance).count();
    return switch (last) {
      case Entry.CustomerMessage message ->
          ABOUT_ORDERS.matcher(message.text()).find()
              ? use(new ToolCall(callId(calls), FIND_ORDERS, Map.of()))
              : reply(WHAT_I_DO);
      case Entry.ToolResults results -> next(results.results().getFirst(), calls);
      default -> throw new IllegalStateException("Nothing to answer after " + last);
    };
  }

  private static Answer next(ToolResult result, int calls) {
    if (result.outcome() == ToolResult.Outcome.ERROR) {
      return reply(UNAVAILABLE);
    }
    return switch (result.tool()) {
      case FIND_ORDERS -> {
        var orders = list(result.content().get("orders"));
        yield orders.isEmpty()
            ? reply("You haven't placed any Orders yet.")
            : use(
                new ToolCall(
                    callId(calls), GET_ORDER, Map.of("orderId", orders.getFirst().get("orderId"))));
      }
      case GET_ORDER ->
          result.outcome() == ToolResult.Outcome.NOT_FOUND
              ? reply("I couldn't find that Order among yours.")
              : reply(latestOrder(result.content()));
      default -> throw new IllegalStateException("The mock never asks for " + result.tool());
    };
  }

  /**
   * "Your latest Order, #7F1C2A3B, is paid. It was placed on 3 November 2026 and paid on 3 November
   * 2026."
   */
  private static String latestOrder(Map<String, Object> order) {
    var history =
        list(order.get("statusHistory")).stream()
            .map(s -> word(s.get("status")) + " on " + day(s.get("at")))
            .toList();
    var reply =
        "Your latest Order, %s, is %s."
            .formatted(order.get("reference"), word(order.get("status")));
    return history.isEmpty() ? reply : reply + " It was " + sentence(history) + ".";
  }

  /** An Order Status as a sentence reads it: {@code PAID} as "paid". */
  private static String word(Object status) {
    return switch (String.valueOf(status)) {
      case "FULFILLED" -> "packed";
      case "DELIVERED" -> "delivered";
      default -> String.valueOf(status).toLowerCase(Locale.ROOT);
    };
  }

  private static String day(Object at) {
    return DAY.format(Instant.parse(String.valueOf(at)));
  }

  /** "a", "a and b", "a, b and c". */
  private static String sentence(List<String> parts) {
    if (parts.size() == 1) {
      return parts.getFirst();
    }
    return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Object value) {
    return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
  }

  private static String callId(int calls) {
    return "mock-call-" + (calls + 1);
  }

  private static Answer use(ToolCall call) {
    return new ToolUse(List.of(call), Usage.NONE);
  }

  private static Answer reply(String text) {
    return new Reply(text, Usage.NONE);
  }
}
