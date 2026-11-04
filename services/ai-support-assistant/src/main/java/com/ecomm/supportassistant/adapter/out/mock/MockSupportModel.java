package com.ecomm.supportassistant.adapter.out.mock;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.domain.Entry;
import com.ecomm.supportassistant.domain.ToolCall;
import com.ecomm.supportassistant.domain.ToolResult;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The scripted provider the stack runs without an API key (ADR 0005). It is deterministic: it picks
 * its tools by keywords in the Customer's latest message, and writes its reply from a template over
 * what they found, naming each record. Each time it is asked, it reads what the turn has looked up
 * so far and asks for the next lookup, or replies.
 *
 * <p>Asked about an Order, it finds the one the message names by its Order reference, or else the
 * Customer's latest, and looks it up with its Order Status history.
 */
public class MockSupportModel implements SupportModel {

  // "parcel" and "package" are how Customers ask, though the glossary's word is Shipment.
  private static final Pattern ABOUT_SHIPMENTS =
      Pattern.compile(
          "\\b(where|parcel|package|ship|shipp\\w*|shipments?|track\\w*|carrier|deliver\\w*|arriv\\w*)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern ABOUT_PAYMENTS =
      Pattern.compile(
          "\\b(charg\\w*|twice|refund\\w*|payments?|bank|card|transactions?|billed)\\b",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern ABOUT_REFUNDS =
      Pattern.compile("\\brefund\\w*\\b", Pattern.CASE_INSENSITIVE);

  private static final Pattern ABOUT_ORDERS =
      Pattern.compile("\\b(orders?|status)\\b", Pattern.CASE_INSENSITIVE);

  /**
   * An Order reference: eight hex digits, with a digit among them or a {@code #} before them, so a
   * word such as "deadbeef" alone isn't one, and not the end of an RMA reference.
   */
  private static final Pattern ORDER_REFERENCE =
      Pattern.compile(
          "(?<![0-9A-Za-z-])(?:#([0-9A-Fa-f]{8})|((?=[A-Fa-f]*\\d)[0-9A-Fa-f]{8}))(?![0-9A-Za-z])");

  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

  static final String FIND_ORDERS = "find_orders";
  static final String GET_ORDER = "get_order";
  static final String GET_SHIPMENT = "get_shipment";
  static final String GET_PAYMENTS = "get_payments";
  static final String GET_PAYMENT_TRANSACTION_HISTORY = "get_payment_transaction_history";

  /** An Order Status whose Order has no Shipment to look up: not yet paid, or never to be. */
  private static final List<String> NOT_SHIPPING = List.of("PLACED", "CANCELLED");

  private static final String UNAVAILABLE =
      "I couldn't look up your %s just now. Please try again in a moment.";

  static final String WHAT_I_DO =
      "I can help with your Orders, their Shipments and their Payments. Ask me, for example,"
          + " \"Where is my Order?\" or \"Why was I charged twice?\"";

  @Override
  public Answer answer(Request request) {
    var transcript = request.transcript();
    var calls = (int) transcript.stream().filter(Entry.ToolUse.class::isInstance).count();
    var message =
        transcript.reversed().stream()
            .filter(Entry.CustomerMessage.class::isInstance)
            .map(Entry.CustomerMessage.class::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No Customer message to answer"));
    return new Turn(message.text(), lookedUp(transcript, message), calls).next();
  }

  /** The results the turn's lookups have come to so far, oldest first. */
  private static List<ToolResult> lookedUp(List<Entry> transcript, Entry.CustomerMessage message) {
    var results = new ArrayList<ToolResult>();
    for (var entry : transcript.subList(transcript.lastIndexOf(message), transcript.size())) {
      if (entry instanceof Entry.ToolResults(var turnResults)) {
        results.addAll(turnResults);
      }
    }
    return results;
  }

  /** One turn: the Customer's message and what has been looked up for it. */
  private record Turn(String message, List<ToolResult> results, int calls) {

    Answer next() {
      var failed =
          results.stream().filter(r -> r.outcome() == ToolResult.Outcome.ERROR).findFirst();
      if (failed.isPresent()) {
        return reply(
            UNAVAILABLE.formatted(
                switch (failed.get().tool()) {
                  case GET_SHIPMENT -> "Shipment";
                  case GET_PAYMENTS, GET_PAYMENT_TRANSACTION_HISTORY -> "Payments";
                  default -> "Orders";
                }));
      }
      var reference = orderReference();
      var aboutPayments = ABOUT_PAYMENTS.matcher(message).find();
      var aboutShipment = ABOUT_SHIPMENTS.matcher(message).find();
      if (reference.isEmpty()
          && !aboutPayments
          && !aboutShipment
          && !ABOUT_ORDERS.matcher(message).find()) {
        return reply(WHAT_I_DO);
      }
      if (aboutPayments && reference.isEmpty()) {
        var history = result(GET_PAYMENT_TRANSACTION_HISTORY);
        if (history.isEmpty()) {
          return use(GET_PAYMENT_TRANSACTION_HISTORY, Map.of());
        }
        var lines = list(history.get().content().get("transactions"));
        // Newest first, so reversed, two at the same instant read oldest first too.
        return reply(lines.isEmpty() ? "You have no Payments yet." : payments(lines.reversed()));
      }
      var found = result(FIND_ORDERS);
      if (found.isEmpty()) {
        return use(
            FIND_ORDERS,
            reference.<Map<String, Object>>map(r -> Map.of("reference", r)).orElse(Map.of()));
      }
      var orders = list(found.get().content().get("orders"));
      if (orders.isEmpty()) {
        return reply(
            reference
                .map(
                    r ->
                        "I couldn't find an Order #%s among yours. Please check the reference in your email."
                            .formatted(r))
                .orElse("You haven't placed any Orders yet."));
      }
      if (reference.isPresent() && orders.size() > 1) {
        return reply(whichOne("#" + reference.get(), orders));
      }
      if (aboutPayments) {
        var payments = result(GET_PAYMENTS);
        if (payments.isEmpty()) {
          return use(GET_PAYMENTS, Map.of("orderId", orders.getFirst().get("orderId")));
        }
        var lines = transactionsOf(payments.get().content());
        return reply(
            lines.isEmpty()
                ? "I couldn't find a Payment for Order %s."
                    .formatted(payments.get().content().get("orderReference"))
                : payments(lines));
      }
      var order = result(GET_ORDER);
      if (order.isEmpty()) {
        return use(GET_ORDER, Map.of("orderId", orders.getFirst().get("orderId")));
      }
      if (order.get().outcome() == ToolResult.Outcome.NOT_FOUND) {
        return reply("I couldn't find that Order among yours.");
      }
      var status = orderStatus(order.get().content(), reference.isEmpty());
      if (!aboutShipment
          || NOT_SHIPPING.contains(String.valueOf(order.get().content().get("status")))) {
        return reply(status);
      }
      var shipment = result(GET_SHIPMENT);
      if (shipment.isEmpty()) {
        return use(GET_SHIPMENT, Map.of("orderId", order.get().content().get("orderId")));
      }
      return reply(status + " " + shipment(shipment.get()));
    }

    /** A reply about charges, or about Refunds if the message asks about them. */
    private String payments(List<Map<String, Object>> transactions) {
      var oldestFirst =
          transactions.stream()
              .sorted(Comparator.comparing(t -> Instant.parse(String.valueOf(t.get("at")))))
              .toList();
      return ABOUT_REFUNDS.matcher(message).find() ? refunds(oldestFirst) : charges(oldestFirst);
    }

    /** The Order reference the message names, upper-cased and without its {@code #}. */
    private Optional<String> orderReference() {
      var matcher = ORDER_REFERENCE.matcher(message);
      if (!matcher.find()) {
        return Optional.empty();
      }
      var digits = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      return Optional.of(digits.toUpperCase(Locale.ROOT));
    }

    private Optional<ToolResult> result(String tool) {
      return results.stream().filter(r -> r.tool().equals(tool)).findFirst();
    }

    private Answer use(String tool, Map<String, Object> input) {
      return new ToolUse(
          List.of(new ToolCall("mock-call-" + (calls + 1), tool, input)), Usage.NONE);
    }
  }

  /**
   * "2 of your Orders have the reference #7F1C2A3B: one paid, placed on 3 November 2026 for 803.99
   * EUR, and one delivered, …. Which one do you mean?"
   */
  private static String whichOne(String reference, List<Map<String, Object>> orders) {
    var each =
        orders.stream()
            .map(
                o ->
                    "one %s, placed on %s for %s"
                        .formatted(
                            word(o.get("status")), day(o.get("placedAt")), money(o.get("total"))))
            .toList();
    return "%d of your Orders have the reference %s: %s. Which one do you mean?"
        .formatted(orders.size(), reference, String.join(", and ", each));
  }

  /**
   * "Your latest Order, #7F1C2A3B, is paid. It was placed on 3 November 2026 and paid on 3 November
   * 2026.", or "Your Order #7F1C2A3B is paid. …" for one the Customer named.
   */
  private static String orderStatus(Map<String, Object> order, boolean latest) {
    var history =
        list(order.get("statusHistory")).stream()
            .map(s -> word(s.get("status")) + " on " + day(s.get("at")))
            .toList();
    var reply =
        (latest ? "Your latest Order, %s, is %s." : "Your Order %s is %s.")
            .formatted(order.get("reference"), word(order.get("status")));
    return history.isEmpty() ? reply : reply + " It was " + sentence(history) + ".";
  }

  /**
   * "It shipped with Mock Carrier, tracking number MC0123456789; its latest Tracking event: in
   * transit on 4 November 2026.", or where it is short of that.
   */
  private static String shipment(ToolResult result) {
    if (result.outcome() == ToolResult.Outcome.NOT_FOUND) {
      return "It has no Shipment yet, so it isn't packed yet.";
    }
    var shipment = result.content();
    var booked =
        "%s, tracking number %s".formatted(shipment.get("carrier"), shipment.get("trackingNumber"));
    return switch (String.valueOf(shipment.get("status"))) {
      case "AWAITING_PICK" -> "It hasn't shipped yet: it is waiting to be picked at the warehouse.";
      case "PICKED" -> "It hasn't shipped yet: it has been picked at the warehouse.";
      case "PACKED" -> "It hasn't shipped yet: it is packed at the warehouse.";
      case "ON_HOLD" -> "Its Shipment is on hold.";
      case "DELIVERED" ->
          "It was delivered on %s by %s.".formatted(day(shipment.get("deliveredAt")), booked);
      default -> {
        var events = list(shipment.get("trackingEvents"));
        yield events.isEmpty()
            ? "It shipped with %s.".formatted(booked)
            : "It shipped with %s; its latest Tracking event: %s on %s."
                .formatted(
                    booked,
                    String.valueOf(events.getLast().get("status"))
                        .replace('_', ' ')
                        .toLowerCase(Locale.ROOT),
                    day(events.getLast().get("at")));
      }
    };
  }

  /**
   * An Order's Payments' transactions as the Payment transaction history's lines, so either answer
   * reads the same.
   */
  private static List<Map<String, Object>> transactionsOf(Map<String, Object> payments) {
    var lines = new ArrayList<Map<String, Object>>();
    for (var payment : list(payments.get("payments"))) {
      for (var transaction : list(payment.get("transactions"))) {
        var line = new HashMap<>(transaction);
        line.put("paymentId", payment.get("paymentId"));
        line.put("orderReference", payments.get("orderReference"));
        lines.add(line);
      }
    }
    return lines;
  }

  /**
   * "You were charged once. On Order #7F1C2A3B, the Authorization of 803.99 EUR on 3 November 2026
   * was only a hold on your card, and the Capture of 803.99 EUR on 4 November 2026 was the
   * charge.": what each Order's Payment transactions came to, oldest first.
   */
  private static String charges(List<Map<String, Object>> transactions) {
    var byOrder = new LinkedHashMap<Object, List<Map<String, Object>>>();
    transactions.forEach(
        t -> byOrder.computeIfAbsent(t.get("orderReference"), r -> new ArrayList<>()).add(t));
    var charged =
        byOrder.entrySet().stream()
            .filter(e -> e.getValue().stream().anyMatch(t -> is(t, "CAPTURE", "APPROVED")))
            .map(e -> String.valueOf(e.getKey()))
            .toList();
    var reply =
        new StringBuilder(
            switch (charged.size()) {
              case 0 -> "Nothing has been charged yet.";
              case 1 -> "You were charged once.";
              default ->
                  "You were charged for %d Orders, %s."
                      .formatted(charged.size(), sentence(charged));
            });
    byOrder.forEach(
        (reference, each) -> {
          var clauses = new ArrayList<String>();
          for (var i = 0; i < each.size(); i++) {
            clause(each.get(i), each.subList(i + 1, each.size())).ifPresent(clauses::add);
          }
          if (!clauses.isEmpty()) {
            reply.append(" On Order %s, %s.".formatted(reference, String.join(", and ", clauses)));
          }
        });
    return reply.toString();
  }

  /** What one Payment transaction came to, given those after it on the same Order. */
  private static Optional<String> clause(
      Map<String, Object> transaction, List<Map<String, Object>> later) {
    var amount = money(transaction.get("amount"));
    var day = day(transaction.get("at"));
    var reason = transaction.get("declineReason");
    var sameLater =
        later.stream()
            .filter(t -> Objects.equals(t.get("paymentId"), transaction.get("paymentId")));
    return Optional.ofNullable(
        switch (transaction.get("kind") + " " + transaction.get("outcome")) {
          case "AUTHORIZATION APPROVED" -> {
            var after = sameLater.toList();
            if (after.stream().anyMatch(t -> is(t, "CAPTURE", "APPROVED"))) {
              yield "the Authorization of %s on %s was only a hold on your card"
                  .formatted(amount, day);
            }
            var voided = after.stream().filter(t -> is(t, "VOID", "APPROVED")).findFirst();
            yield voided.isPresent()
                ? "the Authorization of %s on %s was a hold on your card, released on %s"
                    .formatted(amount, day, day(voided.get().get("at")))
                : ("the Authorization of %s on %s is a hold on your card, not a charge: the"
                        + " Capture charges it when the Order ships, or your bank releases it")
                    .formatted(amount, day);
          }
          case "AUTHORIZATION DECLINED" ->
              "the Authorization of %s on %s was declined (%s), so nothing was taken"
                  .formatted(amount, day, reason);
          case "AUTHORIZATION PENDING" ->
              "the Authorization of %s on %s is waiting for your bank to confirm it"
                  .formatted(amount, day);
          case "CAPTURE APPROVED" ->
              "the Capture of %s on %s was the charge".formatted(amount, day);
          case "CAPTURE DECLINED" ->
              ("the Capture of %s on %s was declined (%s), so nothing was charged, and the"
                      + " Order is held")
                  .formatted(amount, day, reason);
          case "REFUND APPROVED" ->
              "you were refunded %s%s on %s".formatted(amount, refundFor(transaction), day);
          case "REFUND DECLINED" ->
              "a Refund of %s%s on %s was declined (%s)"
                  .formatted(amount, refundFor(transaction), day, reason);
          // A void is told with its Authorization.
          default -> null;
        });
  }

  /**
   * "Your Refund of 129.00 EUR for RMA-3F2A9C1B on Order #7F1C2A3B was approved on 6 November 2026.
   * Your bank may take a few days to show it."
   */
  private static String refunds(List<Map<String, Object>> transactions) {
    var refunds =
        transactions.stream()
            .filter(t -> "REFUND".equals(t.get("kind")) && !"PENDING".equals(t.get("outcome")))
            .toList();
    if (refunds.isEmpty()) {
      return "I couldn't find a Refund on your Payments.";
    }
    var reply = new StringBuilder();
    for (var refund : refunds) {
      var approved = "APPROVED".equals(refund.get("outcome"));
      reply
          .append(reply.isEmpty() ? "" : " ")
          .append(
              "Your Refund of %s%s on Order %s was %s on %s%s."
                  .formatted(
                      money(refund.get("amount")),
                      refundFor(refund),
                      refund.get("orderReference"),
                      approved ? "approved" : "declined",
                      day(refund.get("at")),
                      approved ? "" : " (%s)".formatted(refund.get("declineReason"))));
    }
    if (refunds.stream().anyMatch(r -> "APPROVED".equals(r.get("outcome")))) {
      reply.append(" Your bank may take a few days to show it.");
    }
    return reply.toString();
  }

  /** " for RMA-3F2A9C1B", what a Refund was for, if it says. */
  private static String refundFor(Map<String, Object> refund) {
    return refund.get("reference") == null ? "" : " for " + refund.get("reference");
  }

  private static boolean is(Map<String, Object> transaction, String kind, String outcome) {
    return kind.equals(transaction.get("kind")) && outcome.equals(transaction.get("outcome"));
  }

  /** An Order Status as a sentence reads it: {@code PAID} as "paid". */
  private static String word(Object status) {
    return switch (String.valueOf(status)) {
      case "FULFILLED" -> "packed";
      default -> String.valueOf(status).toLowerCase(Locale.ROOT);
    };
  }

  private static String day(Object at) {
    return DAY.format(Instant.parse(String.valueOf(at)));
  }

  /**
   * An amount as the tools give it, {@code {"amount": "803.99", "currency": "EUR"}}: "803.99 EUR".
   */
  private static String money(Object money) {
    var amount = (Map<?, ?>) money;
    return amount.get("amount") + " " + amount.get("currency");
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

  private static Answer reply(String text) {
    return new Reply(text, Usage.NONE);
  }
}
