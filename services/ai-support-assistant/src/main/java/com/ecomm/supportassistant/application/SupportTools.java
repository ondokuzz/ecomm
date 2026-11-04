package com.ecomm.supportassistant.application;

import com.ecomm.commons.money.Money;
import com.ecomm.supportassistant.application.port.out.FulfillmentShippingPort;
import com.ecomm.supportassistant.application.port.out.FulfillmentShippingPort.ReturnAddress;
import com.ecomm.supportassistant.application.port.out.FulfillmentShippingPort.ShipmentView;
import com.ecomm.supportassistant.application.port.out.Lookup;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort;
import com.ecomm.supportassistant.application.port.out.OrderManagementPort.OrderView;
import com.ecomm.supportassistant.application.port.out.PaymentPort;
import com.ecomm.supportassistant.application.port.out.PaymentPort.History;
import com.ecomm.supportassistant.application.port.out.PaymentPort.HistoryLine;
import com.ecomm.supportassistant.application.port.out.PaymentPort.PaymentTransaction;
import com.ecomm.supportassistant.application.port.out.PaymentPort.PaymentView;
import com.ecomm.supportassistant.application.port.out.ReturnsWarrantyPort;
import com.ecomm.supportassistant.application.port.out.ReturnsWarrantyPort.Eligibility;
import com.ecomm.supportassistant.application.port.out.ReturnsWarrantyPort.RmaView;
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
  static final String FIND_RMAS = "find_rmas";
  static final String GET_RMA = "get_rma";
  static final String CHECK_RETURN_ELIGIBILITY = "check_return_eligibility";

  /** How many of the Customer's Orders {@value #FIND_ORDERS} answers. */
  static final int LATEST_ORDERS = 5;

  /**
   * How many of the Customer's latest Payment transactions {@value
   * #GET_PAYMENT_TRANSACTION_HISTORY} answers.
   */
  static final int HISTORY_LINES = 20;

  /** How many of the Customer's RMAs {@value #FIND_RMAS} answers. */
  static final int LATEST_RMAS = 10;

  /**
   * An RMA reference as a Customer types it: eight hex digits, with or without its {@code RMA-}.
   */
  private static final Pattern RMA_REFERENCE =
      Pattern.compile("(?:RMA-)?([0-9A-Fa-f]{8})", Pattern.CASE_INSENSITIVE);

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
                  false)),
          new ToolDefinition(
              FIND_RMAS,
              "The signed-in Customer's RMAs (returns and warranty claims), newest first, at most "
                  + LATEST_RMAS
                  + ": all of them, or with a reference those with that RMA reference, or with an"
                  + " orderId those of that Order. Each RMA's ID, RMA reference, Order ID and Order"
                  + " reference, Product name, quantity, kind (RETURN or WARRANTY_CLAIM), status"
                  + " (REQUESTED, APPROVED, REJECTED, RECEIVED, EXPIRED, INSPECTED, REFUNDED,"
                  + " EXCHANGED, CLOSED or ON_HOLD), when it was requested and, once approved, its"
                  + " Send-back deadline. A reference usually finds one RMA; when it finds none,"
                  + " none of their RMAs has it.",
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
                          "An RMA reference, such as RMA-3F2A9C1B, with or without the RMA-"),
                      "orderId",
                      Map.of("type", "string", "description", "An Order's ID")),
                  "additionalProperties",
                  false)),
          new ToolDefinition(
              GET_RMA,
              "One of the signed-in Customer's RMAs by its ID: the same, and the Customer's"
                  + " reason, when the Return Period or Warranty Window it was checked against"
                  + " ends, its Refund amount, while it is APPROVED its Return address (where to"
                  + " send the units, by the Send-back deadline; an error if it can't be looked"
                  + " up just now), its Inspection (outcome ACCEPTED, REFUSED, FAULT_CONFIRMED or"
                  + " NO_FAULT_FOUND, and its note), the reference of its Refund once refunded, and"
                  + " its RMA history (each status it moved to, when, and its note: why it was"
                  + " rejected, held or closed), oldest first.",
              Map.of(
                  "type",
                  "object",
                  "properties",
                  Map.of("rmaId", Map.of("type", "string", "description", "The RMA's ID")),
                  "required",
                  List.of("rmaId"),
                  "additionalProperties",
                  false)),
          new ToolDefinition(
              CHECK_RETURN_ELIGIBILITY,
              "What each delivered line of one of the signed-in Customer's Orders, by the Order's"
                  + " ID, is still eligible for: its Order reference, and each line's Variant,"
                  + " Product name, when its Return Period ends, when its Warranty Window ends (null"
                  + " when its Product has no Warranty terms) and how many of its units aren't"
                  + " already in an RMA. Either end may have passed. Not found while the Order isn't"
                  + " delivered. A return or warranty claim is requested on the Order's page.",
              orderIdInput()));

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
  private final ReturnsWarrantyPort returnsWarranty;

  SupportTools(
      OrderManagementPort orderManagement,
      FulfillmentShippingPort fulfillmentShipping,
      PaymentPort payment,
      ReturnsWarrantyPort returnsWarranty) {
    this.orderManagement = orderManagement;
    this.fulfillmentShipping = fulfillmentShipping;
    this.payment = payment;
    this.returnsWarranty = returnsWarranty;
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
      case FIND_RMAS -> {
        String reference = null;
        if (call.input().get("reference") != null) {
          var matcher =
              RMA_REFERENCE.matcher(String.valueOf(call.input().get("reference")).strip());
          if (!matcher.matches()) {
            yield error(
                call,
                "invalidInput",
                "reference must be an RMA reference: eight hex digits, such as RMA-3F2A9C1B");
          }
          reference = matcher.group(1).toUpperCase(Locale.ROOT);
        }
        var orderId = call.input().get("orderId") instanceof String id && !id.isBlank() ? id : null;
        yield result(
            call,
            returnsWarranty.rmas(reference, orderId, LATEST_RMAS),
            rmas -> Map.of("rmas", rmas.stream().map(SupportTools::summary).toList()),
            rmas -> rmas.stream().flatMap(r -> records(r).stream()).distinct().toList(),
            "No RMAs");
      }
      case GET_RMA -> {
        if (!(call.input().get("rmaId") instanceof String rmaId) || rmaId.isBlank()) {
          yield error(call, "invalidInput", "rmaId is required");
        }
        yield result(
            call,
            returnsWarranty.rma(rmaId),
            this::detail,
            SupportTools::records,
            "No RMA " + rmaId + " among the Customer's RMAs");
      }
      case CHECK_RETURN_ELIGIBILITY ->
          withOrderId(
              call,
              orderId ->
                  result(
                      call,
                      returnsWarranty.eligibility(orderId),
                      lines -> eligibility(orderId, lines),
                      lines -> List.of(Reference.order(orderId)),
                      "Order "
                          + orderId
                          + " isn't delivered yet, so nothing on it can be returned yet, or it"
                          + " isn't among the Customer's Orders"));
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

  /** The RMA and its Order, for the reply to link. */
  private static List<Reference> records(RmaView rma) {
    return List.of(Reference.rma(rma.id(), rma.reference()), Reference.order(rma.orderId()));
  }

  private static LinkedHashMap<String, Object> summary(RmaView rma) {
    var summary = new LinkedHashMap<String, Object>();
    summary.put("rmaId", rma.id());
    summary.put("reference", rma.reference());
    summary.put("orderId", rma.orderId());
    summary.put("orderReference", Reference.orderReference(rma.orderId()));
    summary.put("productName", rma.productName());
    summary.put("quantity", rma.quantity());
    summary.put("kind", rma.kind());
    summary.put("status", rma.status());
    summary.put("requestedAt", rma.requestedAt().toString());
    summary.put("sendBackBy", text(rma.sendBackBy()));
    return summary;
  }

  /**
   * The RMA in full. While it is approved, its Return address is read too: the Customer needs it
   * only until the units are sent back. Unread, the RMA is answered without it.
   */
  private Map<String, Object> detail(RmaView rma) {
    var detail = summary(rma);
    detail.put("reason", rma.reason());
    detail.put("windowEndsAt", rma.windowEndsAt().toString());
    detail.put("refundAmount", rma.refundAmount() == null ? null : money(rma.refundAmount()));
    detail.put("returnAddress", returnAddressWhileApproved(rma));
    var inspection = rma.inspection();
    detail.put(
        "inspection",
        inspection == null
            ? null
            : nullable("outcome", inspection.outcome(), "note", inspection.note()));
    detail.put("refundReference", rma.refundReference());
    detail.put(
        "history",
        rma.history().stream()
            .map(s -> nullable("status", s.status(), "at", s.at().toString(), "note", s.note()))
            .toList());
    return detail;
  }

  private static Map<String, Object> eligibility(String orderId, List<Eligibility> lines) {
    var content = new LinkedHashMap<String, Object>();
    content.put("orderId", orderId);
    content.put("orderReference", Reference.orderReference(orderId));
    content.put(
        "lines",
        lines.stream()
            .map(
                l ->
                    nullable(
                        "variantId",
                        l.variantId(),
                        "productName",
                        l.productName(),
                        "returnPeriodEndsAt",
                        l.returnPeriodEndsAt().toString(),
                        "warrantyWindowEndsAt",
                        text(l.warrantyWindowEndsAt()),
                        "unitsFree",
                        l.unitsFree()))
            .toList());
    return content;
  }

  /**
   * Where the approved RMA's units go: its Warehouse's Return address, an error saying it can't be
   * looked up just now, or null when it isn't approved or its Warehouse isn't configured.
   */
  private Map<String, Object> returnAddressWhileApproved(RmaView rma) {
    if (!"APPROVED".equals(rma.status()) || rma.warehouseId() == null) {
      return null;
    }
    return switch (fulfillmentShipping.returnAddress(rma.warehouseId())) {
      case Lookup.Found<ReturnAddress>(var address) ->
          Map.of(
              "name", address.name(),
              "street", address.street(),
              "postcode", address.postcode(),
              "city", address.city(),
              "country", address.country());
      case Lookup.NotFound<ReturnAddress>() -> null;
      case Lookup.Failed<ReturnAddress>(var reason) ->
          Map.of("error", "unavailable", "message", "It can't be looked up just now: " + reason);
    };
  }

  /** A map of these keys and values, which may be null. */
  private static Map<String, Object> nullable(Object... keysAndValues) {
    var map = new LinkedHashMap<String, Object>();
    for (var i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  /** An instant as the model reads it, or null for none. */
  private static String text(Instant instant) {
    return instant == null ? null : instant.toString();
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
