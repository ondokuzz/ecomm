package com.ecomm.supportassistant.application;

import com.ecomm.commons.money.Money;
import com.ecomm.supportassistant.application.port.out.Lookup;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort.OrderView;
import com.ecomm.supportassistant.application.port.out.SupportModel.ToolDefinition;
import com.ecomm.supportassistant.domain.Reference;
import com.ecomm.supportassistant.domain.ToolCall;
import com.ecomm.supportassistant.domain.ToolResult;
import com.ecomm.supportassistant.domain.ToolResult.Outcome;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The lookups the model may ask for. Each is a read the Customer could make themselves, made with
 * their own token, and answers the context's JSON trimmed to what its description promises. A
 * context's 404 is a result saying it wasn't found, and a failure one saying it couldn't be looked
 * up now; neither fails the turn. No tool changes anything.
 */
class SupportTools {

  static final String FIND_ORDERS = "find_orders";
  static final String GET_ORDER = "get_order";

  /** How many of the Customer's latest Orders {@value #FIND_ORDERS} answers. */
  static final int LATEST_ORDERS = 5;

  static final List<ToolDefinition> DEFINITIONS =
      List.of(
          new ToolDefinition(
              FIND_ORDERS,
              "The signed-in Customer's latest Orders, newest first, at most "
                  + LATEST_ORDERS
                  + ": each Order's ID, Order reference, Order Status, when it was placed and its"
                  + " total.",
              Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)),
          new ToolDefinition(
              GET_ORDER,
              "One of the signed-in Customer's Orders by its ID: its Order reference, Order"
                  + " Status, when it was placed, its total, its Order Lines (Variant, quantity,"
                  + " unit price) and its Order Status history (each Status it moved to, and"
                  + " when), oldest first.",
              Map.of(
                  "type",
                  "object",
                  "properties",
                  Map.of("orderId", Map.of("type", "string", "description", "The Order's ID")),
                  "required",
                  List.of("orderId"),
                  "additionalProperties",
                  false)));

  private final OrderManagementPort orderManagement;

  SupportTools(OrderManagementPort orderManagement) {
    this.orderManagement = orderManagement;
  }

  ToolResult run(ToolCall call) {
    return switch (call.tool()) {
      case FIND_ORDERS ->
          result(
              call,
              orderManagement.latestOrders(LATEST_ORDERS),
              orders -> Map.of("orders", orders.stream().map(SupportTools::summary).toList()),
              orders -> orders.stream().map(o -> Reference.order(o.id())).toList(),
              "No Orders");
      case GET_ORDER -> {
        if (!(call.input().get("orderId") instanceof String orderId) || orderId.isBlank()) {
          yield error(call, "invalidInput", "orderId is required");
        }
        yield result(
            call,
            orderManagement.order(orderId),
            SupportTools::detail,
            order -> List.of(Reference.order(order.id())),
            "No Order " + orderId + " among the Customer's Orders");
      }
      default -> error(call, "unknownTool", "There is no tool " + call.tool());
    };
  }

  private static <T> ToolResult result(
      ToolCall call,
      Lookup<T> lookup,
      Function<T, Map<String, Object>> content,
      Function<T, List<Reference>> records,
      String notFound) {
    return switch (lookup) {
      case Lookup.Found<T>(var value) ->
          new ToolResult(
              call.id(), call.tool(), Outcome.FOUND, content.apply(value), records.apply(value));
      case Lookup.NotFound<T>() ->
          new ToolResult(
              call.id(),
              call.tool(),
              Outcome.NOT_FOUND,
              Map.of("error", "notFound", "message", notFound),
              List.of());
      case Lookup.Failed<T>(var reason) ->
          new ToolResult(
              call.id(),
              call.tool(),
              Outcome.ERROR,
              Map.of(
                  "error", "unavailable", "message", "It can't be looked up just now: " + reason),
              List.of());
    };
  }

  private static ToolResult error(ToolCall call, String error, String message) {
    return new ToolResult(
        call.id(),
        call.tool(),
        Outcome.ERROR,
        Map.of("error", error, "message", message),
        List.of());
  }

  private static Map<String, Object> summary(OrderView order) {
    var summary = new LinkedHashMap<String, Object>();
    summary.put("orderId", order.id());
    summary.put("reference", Reference.orderReference(order.id()));
    summary.put("status", order.status());
    summary.put("placedAt", order.placedAt().toString());
    summary.put("total", money(order.total()));
    return summary;
  }

  private static Map<String, Object> detail(OrderView order) {
    var detail = summary(order);
    detail.put(
        "lines",
        order.lines().stream()
            .map(
                l ->
                    Map.<String, Object>of(
                        "variantId", l.variantId(),
                        "quantity", l.quantity(),
                        "unitPrice", money(l.unitPrice())))
            .toList());
    detail.put(
        "statusHistory",
        order.statusHistory().stream()
            .map(s -> Map.<String, Object>of("status", s.status(), "at", s.at().toString()))
            .toList());
    return detail;
  }

  /** An amount as the Customer reads it, {@code "803.99"}, in its Currency. */
  private static Map<String, Object> money(Money money) {
    var amount =
        BigDecimal.valueOf(money.amountMinor())
            .movePointLeft(money.currency().getDefaultFractionDigits())
            .toPlainString();
    return Map.of("amount", amount, "currency", money.currency().getCurrencyCode());
  }
}
