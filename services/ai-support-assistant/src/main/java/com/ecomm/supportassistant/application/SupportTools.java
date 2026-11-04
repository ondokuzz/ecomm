package com.ecomm.supportassistant.application;

import com.ecomm.commons.money.Money;
import com.ecomm.supportassistant.application.port.out.FulfillmentShippingPort;
import com.ecomm.supportassistant.application.port.out.FulfillmentShippingPort.ShipmentView;
import com.ecomm.supportassistant.application.port.out.Lookup;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort.OrderView;
import com.ecomm.supportassistant.application.port.out.PaymentPort;
import com.ecomm.supportassistant.application.port.out.PaymentPort.History;
import com.ecomm.supportassistant.application.port.out.PaymentPort.HistoryLine;
import com.ecomm.supportassistant.application.port.out.PaymentPort.PaymentTransaction;
import com.ecomm.supportassistant.application.port.out.PaymentPort.PaymentView;
import com.ecomm.supportassistant.application.port.out.SupportModel.ToolDefinition;
import com.ecomm.supportassistant.domain.Reference;
import com.ecomm.supportassistant.domain.ToolCall;
import com.ecomm.supportassistant.domain.ToolResult;
import com.ecomm.supportassistant.domain.ToolResult.Outcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The lookups the model may ask for. Each is a read the Customer could make themselves, made with
 * their own token, and answers the context's JSON trimmed to what its description promises. A
 * context's 404 is a result saying it wasn't found, and a failure one saying it couldn't be looked
 * up now; neither fails the turn. No tool changes anything.
 */
class SupportTools {

  static final String FIND_ORDERS = "find_orders";
  static final String GET_ORDER = "get_order";
  static final String GET_SHIPMENT = "get_shipment";
  static final String GET_PAYMENTS = "get_payments";
  static final String GET_PAYMENT_TRANSACTION_HISTORY = "get_payment_transaction_history";

  /** How many of the Customer's Orders {@value #FIND_ORDERS} answers. */
  static final int LATEST_ORDERS = 5;

  /**
   * How many of the Customer's latest Payment transactions {@value
   * #GET_PAYMENT_TRANSACTION_HISTORY} answers.
   */
  static final int HISTORY_LINES = 20;

  /** An Order reference as a Customer types it: eight hex digits, with or without its {@code #}. */
  private static final Pattern ORDER_REFERENCE = Pattern.compile("#?([0-9A-Fa-f]{8})");

  static final List<ToolDefinition> DEFINITIONS =
      List.of(
          new ToolDefinition(
              FIND_ORDERS,
              "The signed-in Customer's latest Orders, or with a reference those with that Order"
                  + " reference, newest first, at most "
                  + LATEST_ORDERS
                  + ": each Order's ID, Order reference, Order Status, when it was placed and its"
                  + " total. A reference usually finds one Order; when it finds several, ask"
                  + " which one the Customer means, and when it finds none, none of their Orders"
                  + " has it.",
              Map.of(
                  "type",
                  "object",
                  "properties",
                  Map.of(
                      "reference",
                      Map.of(
                          "type",
                          "string",
                          "description",
                          "An Order reference, such as #EA2CA751, with or without the #")),
                  "additionalProperties",
                  false)),
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
                  false)),
          new ToolDefinition(
              GET_SHIPMENT,
              "The Shipment of one of the signed-in Customer's Orders, by the Order's ID: its"
                  + " Order reference, the Shipment's status (AWAITING_PICK, PICKED, PACKED,"
                  + " SHIPPED, DELIVERED or ON_HOLD), its Carrier and tracking number once it is"
                  + " booked, its Tracking events (each status and when), oldest first, and when it"
                  + " was delivered. Not found while the Order has no Shipment, which it gets once"
                  + " it is paid.",
              orderIdInput()),
          new ToolDefinition(
              GET_PAYMENTS,
              "The Payments of one of the signed-in Customer's Orders, by the Order's ID: its"
                  + " Order reference, and its Payments, newest first: each Payment's ID, status, amount and decline reason, and its Payment"
                  + " transactions, oldest first (kind AUTHORIZATION, VOID, CAPTURE or REFUND; its"
                  + " amount; its outcome APPROVED, DECLINED or PENDING; the decline reason; when;"
                  + " and what a Refund was for). An Authorization is a hold on the card, the"
                  + " Capture the charge.",
              orderIdInput()),
          new ToolDefinition(
              GET_PAYMENT_TRANSACTION_HISTORY,
              "The signed-in Customer's Payment transactions across all their Payments, newest"
                  + " first, at most "
                  + HISTORY_LINES
                  + ", optionally from (inclusive) and to (exclusive) an instant: each one's kind,"
                  + " amount, outcome, decline reason, when, what a Refund was for, its Payment's"
                  + " ID and status, and its Order's ID and Order reference; and how many there"
                  + " are in all.",
              Map.of(
                  "type",
                  "object",
                  "properties",
                  Map.of(
                      "from",
                      Map.of(
                          "type",
                          "string",
                          "description",
                          "The earliest, inclusive, as an ISO 8601 instant such as"
                              + " 2026-11-01T00:00:00Z"),
                      "to",
                      Map.of(
                          "type",
                          "string",
                          "description",
                          "The latest, exclusive, as an ISO 8601 instant")),
                  "additionalProperties",
                  false)));

  private static Map<String, Object> orderIdInput() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of("orderId", Map.of("type", "string", "description", "The Order's ID")),
        "required",
        List.of("orderId"),
        "additionalProperties",
        false);
  }

  private final OrderManagementPort orderManagement;
  private final FulfillmentShippingPort fulfillmentShipping;
  private final PaymentPort payment;

  SupportTools(
      OrderManagementPort orderManagement,
      FulfillmentShippingPort fulfillmentShipping,
      PaymentPort payment) {
    this.orderManagement = orderManagement;
    this.fulfillmentShipping = fulfillmentShipping;
    this.payment = payment;
  }

  ToolResult run(ToolCall call) {
    return switch (call.tool()) {
      case FIND_ORDERS -> {
        var reference = call.input().get("reference");
        if (reference == null) {
          yield orders(call, orderManagement.latestOrders(LATEST_ORDERS));
        }
        var matcher = ORDER_REFERENCE.matcher(String.valueOf(reference).strip());
        if (!matcher.matches()) {
          yield error(
              call,
              "invalidInput",
              "reference must be an Order reference: eight hex digits, such as #EA2CA751");
        }
        yield orders(
            call,
            orderManagement.ordersByReference(
                matcher.group(1).toUpperCase(Locale.ROOT), LATEST_ORDERS));
      }
      case GET_ORDER ->
          withOrderId(
              call,
              orderId ->
                  result(
                      call,
                      orderManagement.order(orderId),
                      SupportTools::detail,
                      order -> List.of(Reference.order(order.id())),
                      "No Order " + orderId + " among the Customer's Orders"));
      case GET_SHIPMENT ->
          withOrderId(
              call,
              orderId ->
                  result(
                      call,
                      fulfillmentShipping.shipment(orderId),
                      SupportTools::shipment,
                      shipment -> List.of(Reference.order(shipment.orderId())),
                      "No Shipment for the Order "
                          + orderId
                          + ": it has none yet, or it isn't among the Customer's Orders"));
      case GET_PAYMENTS ->
          withOrderId(
              call,
              orderId ->
                  result(
                      call,
                      payment.payments(orderId),
                      payments -> payments(orderId, payments),
                      payments ->
                          payments.isEmpty() ? List.of() : List.of(Reference.order(orderId)),
                      "No Payments for the Order " + orderId));
      case GET_PAYMENT_TRANSACTION_HISTORY -> {
        Instant from;
        Instant to;
        try {
          from = instant(call.input().get("from"));
          to = instant(call.input().get("to"));
        } catch (DateTimeParseException e) {
          yield error(
              call,
              "invalidInput",
              "from and to must be ISO 8601 instants, such as 2026-11-01T00:00:00Z");
        }
        yield result(
            call,
            payment.transactionHistory(from, to, HISTORY_LINES),
            SupportTools::history,
            history ->
                history.lines().stream()
                    .map(HistoryLine::orderId)
                    .distinct()
                    .map(Reference::order)
                    .toList(),
            "No Payment transactions");
      }
      default -> error(call, "unknownTool", "There is no tool " + call.tool());
    };
  }

  /** The lookup of the Order the call names, or an error result if it names none. */
  private static ToolResult withOrderId(ToolCall call, Function<String, ToolResult> lookup) {
    if (!(call.input().get("orderId") instanceof String orderId) || orderId.isBlank()) {
      return error(call, "invalidInput", "orderId is required");
    }
    return lookup.apply(orderId);
  }

  private static ToolResult orders(ToolCall call, Lookup<List<OrderView>> lookup) {
    return result(
        call,
        lookup,
        orders -> Map.of("orders", orders.stream().map(SupportTools::summary).toList()),
        orders -> orders.stream().map(o -> Reference.order(o.id())).toList(),
        "No Orders");
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

  private static Map<String, Object> shipment(ShipmentView shipment) {
    var content = new LinkedHashMap<String, Object>();
    content.put("orderId", shipment.orderId());
    content.put("orderReference", Reference.orderReference(shipment.orderId()));
    content.put("status", shipment.status());
    content.put("carrier", shipment.carrier());
    content.put("trackingNumber", shipment.trackingNumber());
    content.put(
        "trackingEvents",
        shipment.trackingEvents().stream()
            .map(e -> Map.<String, Object>of("status", e.status(), "at", e.at().toString()))
            .toList());
    content.put(
        "deliveredAt", shipment.deliveredAt() == null ? null : shipment.deliveredAt().toString());
    return content;
  }

  private static Map<String, Object> payments(String orderId, List<PaymentView> payments) {
    var content = new LinkedHashMap<String, Object>();
    content.put("orderId", orderId);
    content.put("orderReference", Reference.orderReference(orderId));
    content.put(
        "payments",
        payments.stream()
            .map(
                p -> {
                  var each = new LinkedHashMap<String, Object>();
                  each.put("paymentId", p.id());
                  each.put("status", p.status());
                  each.put("amount", money(p.amount()));
                  each.put("declineReason", p.declineReason());
                  each.put(
                      "transactions",
                      p.transactions().stream().map(SupportTools::transaction).toList());
                  return each;
                })
            .toList());
    return content;
  }

  private static Map<String, Object> history(History history) {
    var content = new LinkedHashMap<String, Object>();
    content.put(
        "transactions",
        history.lines().stream()
            .map(
                line -> {
                  var each = transaction(line.transaction());
                  each.put("paymentId", line.paymentId());
                  each.put("paymentStatus", line.paymentStatus());
                  each.put("orderId", line.orderId());
                  each.put("orderReference", Reference.orderReference(line.orderId()));
                  return each;
                })
            .toList());
    content.put("total", history.total());
    return content;
  }

  private static LinkedHashMap<String, Object> transaction(PaymentTransaction transaction) {
    var each = new LinkedHashMap<String, Object>();
    each.put("kind", transaction.kind());
    each.put("amount", money(transaction.amount()));
    each.put("outcome", transaction.outcome());
    each.put("declineReason", transaction.declineReason());
    each.put("at", transaction.at().toString());
    each.put("reference", transaction.reference());
    return each;
  }

  /** An instant the model gave, or null for none. */
  private static Instant instant(Object value) {
    return value == null ? null : Instant.parse(String.valueOf(value));
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
