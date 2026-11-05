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
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The scripted provider the stack runs without an API key (ADR 0005). It is deterministic: it picks
 * its tools by keywords in the Customer's latest message, and writes its reply from a template over
 * what they found, naming each record. Each time it is asked, it reads what the turn has looked up
 * so far and asks for the next lookup, or replies.
 *
 * <p>Asked about an Order, it finds the one the message names by its Order reference, or else the
 * Customer's latest, and looks it up with its Order Status history. Asked about an RMA by its RMA
 * reference, it looks that one up; asked about returns, whether the Order's units can still be
 * returned or claimed under warranty, or else the Customer's RMAs. Asked about a Product, it
 * searches the Catalog for the message's words, and answers the Product the message names with its
 * Variants' Stock and Prices or its Warranty terms, or else lists what it found.
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

  private static final Pattern ABOUT_RETURNS =
      Pattern.compile("\\b(return\\w*|rmas?|warrant\\w*)\\b", Pattern.CASE_INSENSITIVE);

  /** Asking whether something can still be returned or claimed, rather than where a return is. */
  private static final Pattern ABOUT_ELIGIBILITY =
      Pattern.compile(
          "\\b(can|could|still|eligible|eligibility|under warranty|how long|until|want|request|start)\\b",
          Pattern.CASE_INSENSITIVE);

  /** Asking whether a Product is in Stock, or what it costs. */
  private static final Pattern ABOUT_STOCK =
      Pattern.compile(
          "\\b(stock|stocked|available|availability|prices?|costs?|how much)\\b",
          Pattern.CASE_INSENSITIVE);

  /** Asking what a Product's Warranty terms are. */
  private static final Pattern ABOUT_WARRANTY_TERMS =
      Pattern.compile("\\b(warrant\\w*|guarantee\\w*)\\b", Pattern.CASE_INSENSITIVE);

  /** Asking for Products that fit a need. */
  private static final Pattern ABOUT_SEARCH =
      Pattern.compile(
          "\\b(looking for|recommend\\w*|suggest\\w*|search\\w*|do you (have|sell)|show me|find"
              + " me|alternatives?)\\b",
          Pattern.CASE_INSENSITIVE);

  /**
   * Words saying the question is about the Customer's own things, not the Catalog's: "Is my phone
   * still under warranty?" is about an Order's Warranty Window.
   */
  private static final Pattern ABOUT_MINE =
      Pattern.compile(
          "\\b(my|mine|i bought|i ordered|orders?|rmas?|claim\\w*)\\b", Pattern.CASE_INSENSITIVE);

  /** The words of a question about Products that aren't what to search for. */
  private static final Set<String> NOT_SEARCHED =
      Set.of(
          "a",
          "about",
          "am",
          "an",
          "and",
          "any",
          "are",
          "available",
          "availability",
          "be",
          "best",
          "buy",
          "can",
          "come",
          "comes",
          "cost",
          "costs",
          "could",
          "do",
          "does",
          "find",
          "for",
          "get",
          "good",
          "guarantee",
          "has",
          "have",
          "how",
          "i",
          "i'm",
          "im",
          "in",
          "is",
          "it",
          "its",
          "it's",
          "look",
          "looking",
          "me",
          "much",
          "need",
          "of",
          "on",
          "one",
          "or",
          "please",
          "price",
          "prices",
          "recommend",
          "search",
          "sell",
          "show",
          "some",
          "still",
          "stock",
          "stocked",
          "suggest",
          "tell",
          "that",
          "the",
          "there",
          "this",
          "to",
          "want",
          "warranty",
          "what",
          "what's",
          "whats",
          "which",
          "with",
          "would",
          "you");

  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}'()-]+");

  private static final Pattern ABOUT_ORDERS =
      Pattern.compile("\\b(orders?|status)\\b", Pattern.CASE_INSENSITIVE);

  /**
   * An Order reference: eight hex digits, with a digit among them or a {@code #} before them, so a
   * word such as "deadbeef" alone isn't one, and not the end of an RMA reference.
   */
  private static final Pattern ORDER_REFERENCE =
      Pattern.compile(
          "(?<![0-9A-Za-z-])(?:#([0-9A-Fa-f]{8})|((?=[A-Fa-f]*\\d)[0-9A-Fa-f]{8}))(?![0-9A-Za-z])");

  /** An RMA reference: {@code RMA-} and eight hex digits, in either case. */
  private static final Pattern RMA_REFERENCE =
      Pattern.compile(
          "(?<![0-9A-Za-z])RMA[- ]?([0-9A-Fa-f]{8})(?![0-9A-Za-z])", Pattern.CASE_INSENSITIVE);

  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

  static final String FIND_ORDERS = "find_orders";
  static final String GET_ORDER = "get_order";
  static final String GET_SHIPMENT = "get_shipment";
  static final String GET_PAYMENTS = "get_payments";
  static final String GET_PAYMENT_TRANSACTION_HISTORY = "get_payment_transaction_history";
  static final String FIND_RMAS = "find_rmas";
  static final String GET_RMA = "get_rma";
  static final String CHECK_RETURN_ELIGIBILITY = "check_return_eligibility";
  static final String SEARCH_PRODUCTS = "search_products";
  static final String GET_PRODUCT = "get_product";

  /** An Order Status whose Order has no Shipment to look up: not yet paid, or never to be. */
  private static final List<String> NOT_SHIPPING = List.of("PLACED", "CANCELLED");

  private static final String UNAVAILABLE =
      "I couldn't look up your %s just now. Please try again in a moment.";

  static final String WHAT_I_DO =
      "I can help with your Orders, their Shipments and their Payments, and your returns and"
          + " warranty claims, and our Products. Ask me, for example, \"Where is my Order?\", \"Why"
          + " was I charged twice?\", \"Can I still return my Order?\" or \"Is the Pixel 9 in"
          + " stock?\"";

  static final String WHICH_PRODUCT =
      "Which Product do you mean? Tell me its name, or what you're looking for, and I'll look it"
          + " up.";

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
    return new Turn(message.text(), message.at(), lookedUp(transcript, message), calls).next();
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

  /**
   * One turn: the Customer's message, when they sent it, and what has been looked up for it.
   *
   * @param at when the message was sent, which a window's end is compared with
   */
  private record Turn(String message, Instant at, List<ToolResult> results, int calls) {

    Answer next() {
      var failed =
          results.stream().filter(r -> r.outcome() == ToolResult.Outcome.ERROR).findFirst();
      if (failed.isPresent()) {
        if (List.of(SEARCH_PRODUCTS, GET_PRODUCT).contains(failed.get().tool())) {
          return reply("I couldn't look up our Products just now. Please try again in a moment.");
        }
        return reply(
            UNAVAILABLE.formatted(
                switch (failed.get().tool()) {
                  case GET_SHIPMENT -> "Shipment";
                  case GET_PAYMENTS, GET_PAYMENT_TRANSACTION_HISTORY -> "Payments";
                  case FIND_RMAS, GET_RMA, CHECK_RETURN_ELIGIBILITY -> "returns";
                  default -> "Orders";
                }));
      }
      var rmaReference = rmaReference();
      if (rmaReference.isPresent()) {
        return rmaByReference(rmaReference.get());
      }
      var reference = orderReference();
      if (reference.isEmpty() && aboutProducts()) {
        return products();
      }
      if (ABOUT_RETURNS.matcher(message).find()) {
        return ABOUT_ELIGIBILITY.matcher(message).find()
            ? eligibility(reference)
            : reference.isPresent() ? withOrder(reference, this::rmasOf) : rmasOf(null);
      }
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
      return withOrder(
          reference, found -> aboutOrder(found, reference.isEmpty(), aboutPayments, aboutShipment));
    }

    /**
     * Finds the Order the message names by its Order reference, or else the Customer's latest, and
     * answers about it; or says it found none, or asks which one when several have the reference.
     */
    private Answer withOrder(
        Optional<String> reference, Function<Map<String, Object>, Answer> about) {
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
      return about.apply(orders.getFirst());
    }

    /** The Order's Payments, or its Order Status and, asked where it is, its Shipment. */
    private Answer aboutOrder(
        Map<String, Object> found, boolean latest, boolean aboutPayments, boolean aboutShipment) {
      if (aboutPayments) {
        var payments = result(GET_PAYMENTS);
        if (payments.isEmpty()) {
          return use(GET_PAYMENTS, Map.of("orderId", found.get("orderId")));
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
        return use(GET_ORDER, Map.of("orderId", found.get("orderId")));
      }
      if (order.get().outcome() == ToolResult.Outcome.NOT_FOUND) {
        return reply("I couldn't find that Order among yours.");
      }
      var status = orderStatus(order.get().content(), latest);
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

    /**
     * What the Order's units can still be claimed for, until when, and where to request it; the
     * Order the message names, or else the Customer's latest.
     */
    private Answer eligibility(Optional<String> reference) {
      return withOrder(
          reference,
          order -> {
            var eligibility = result(CHECK_RETURN_ELIGIBILITY);
            if (eligibility.isEmpty()) {
              return use(CHECK_RETURN_ELIGIBILITY, Map.of("orderId", order.get("orderId")));
            }
            if (eligibility.get().outcome() == ToolResult.Outcome.NOT_FOUND) {
              return reply(
                  ("Order %s isn't delivered yet, so nothing on it can be returned yet. Once it is,"
                          + " you can request a return on its page.")
                      .formatted(order.get("reference")));
            }
            return reply(MockSupportModel.eligibility(eligibility.get().content(), reference, at));
          });
    }

    /**
     * The Customer's RMAs, or those of {@code order}: the one explained, or several listed.
     *
     * @param order null for all of them
     */
    private Answer rmasOf(Map<String, Object> order) {
      var found = result(FIND_RMAS);
      if (found.isEmpty()) {
        return use(FIND_RMAS, order == null ? Map.of() : Map.of("orderId", order.get("orderId")));
      }
      var rmas = list(found.get().content().get("rmas"));
      if (rmas.isEmpty()) {
        return reply(
            order == null
                ? "You have no RMAs. To request a return, open the Order's page under My Orders."
                : "You have no RMAs on Order %s. To request a return, open its page."
                    .formatted(order.get("reference")));
      }
      if (rmas.size() == 1) {
        return rma(rmas.getFirst());
      }
      var each =
          rmas.stream()
              .map(
                  r ->
                      "%s, %s%s, %s"
                          .formatted(
                              r.get("reference"),
                              claim(r),
                              order == null ? " on Order " + r.get("orderReference") : "",
                              word(r.get("status"))))
              .toList();
      return reply(
          "You have %d RMAs%s: %s; and %s."
              .formatted(
                  rmas.size(),
                  order == null ? "" : " on Order " + order.get("reference"),
                  String.join("; ", each.subList(0, each.size() - 1)),
                  each.getLast()));
    }

    /** The RMA the message names by its RMA reference: its status and next step. */
    private Answer rmaByReference(String reference) {
      var found = result(FIND_RMAS);
      if (found.isEmpty()) {
        return use(FIND_RMAS, Map.of("reference", reference));
      }
      var rmas = list(found.get().content().get("rmas"));
      if (rmas.isEmpty()) {
        return reply(
            "I couldn't find an RMA %s among yours. Please check the reference in your email."
                .formatted(reference));
      }
      return rma(rmas.getFirst());
    }

    /** One RMA, looked up in full: its status and next step. */
    private Answer rma(Map<String, Object> summary) {
      var rma = result(GET_RMA);
      if (rma.isEmpty()) {
        return use(GET_RMA, Map.of("rmaId", summary.get("rmaId")));
      }
      if (rma.get().outcome() == ToolResult.Outcome.NOT_FOUND) {
        return reply("I couldn't find that RMA among yours.");
      }
      return reply(rmaStatus(rma.get().content()));
    }

    /** Whether the message asks about the Catalog's Products, rather than the Customer's own. */
    private boolean aboutProducts() {
      return !ABOUT_MINE.matcher(message).find()
          && !ABOUT_PAYMENTS.matcher(message).find()
          && (ABOUT_STOCK.matcher(message).find()
              || ABOUT_WARRANTY_TERMS.matcher(message).find()
              || ABOUT_SEARCH.matcher(message).find());
    }

    /**
     * Searches the Catalog for the message's words; then the Product it names, looked up, with its
     * Warranty terms or its Variants' Stock and Prices, or else the Products found.
     */
    private Answer products() {
      var query = searchedWords();
      if (query.isEmpty()) {
        return reply(WHICH_PRODUCT);
      }
      var found = result(SEARCH_PRODUCTS);
      if (found.isEmpty()) {
        return use(SEARCH_PRODUCTS, Map.of("query", query));
      }
      var products = list(found.get().content().get("products"));
      if (products.isEmpty()) {
        return reply(
            "I couldn't find a Product for \"%s\". Try other words, or browse the Catalog."
                .formatted(query));
      }
      var aboutWarranty = ABOUT_WARRANTY_TERMS.matcher(message).find();
      var named = named(products);
      if (named.isEmpty() || !(aboutWarranty || ABOUT_STOCK.matcher(message).find())) {
        return reply(MockSupportModel.found(query, products));
      }
      var product = result(GET_PRODUCT);
      if (product.isEmpty()) {
        return use(GET_PRODUCT, Map.of("sku", named.get().get("sku")));
      }
      if (product.get().outcome() == ToolResult.Outcome.NOT_FOUND) {
        return reply("I couldn't find the %s in the Catalog.".formatted(named.get().get("name")));
      }
      var content = product.get().content();
      return reply(aboutWarranty ? warrantyTerms(content) : stock(content, message));
    }

    /** The message's words that say which Products, as typed, or empty if none do. */
    private String searchedWords() {
      return WORD.matcher(message)
          .results()
          .map(MatchResult::group)
          .filter(w -> !NOT_SEARCHED.contains(w.toLowerCase(Locale.ROOT)))
          .collect(Collectors.joining(" "));
    }

    /**
     * The Product whose name the message names most of, at least half of its words: "the Pixel 9"
     * names the Google Pixel 9 rather than the Google Pixel 9 Pro.
     */
    private Optional<Map<String, Object>> named(List<Map<String, Object>> products) {
      var said = words(message);
      Map<String, Object> best = null;
      var bestSaid = 0;
      var bestUnsaid = 0;
      for (var product : products) {
        var name = words(String.valueOf(product.get("name")));
        var inMessage = (int) name.stream().filter(said::contains).count();
        var unsaid = name.size() - inMessage;
        if (inMessage * 2 >= name.size()
            && inMessage > 0
            && (best == null
                || inMessage > bestSaid
                || (inMessage == bestSaid && unsaid < bestUnsaid))) {
          best = product;
          bestSaid = inMessage;
          bestUnsaid = unsaid;
        }
      }
      return Optional.ofNullable(best);
    }

    /** The RMA reference the message names, as {@code RMA-3F2A9C1B}. */
    private Optional<String> rmaReference() {
      var matcher = RMA_REFERENCE.matcher(message);
      return matcher.find()
          ? Optional.of("RMA-" + matcher.group(1).toUpperCase(Locale.ROOT))
          : Optional.empty();
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
   * "Your RMA RMA-3F2A9C1B, a return of 1 Pixel 9 on Order #7F1C2A3B, is approved. Please send it
   * back by 21 November 2026, …": where it stands, and what happens next.
   */
  private static String rmaStatus(Map<String, Object> rma) {
    var history = list(rma.get("history"));
    var status = String.valueOf(rma.get("status"));
    var step =
        history.reversed().stream()
            .filter(s -> status.equals(s.get("status")))
            .findFirst()
            .orElse(Map.of());
    var on = step.containsKey("at") ? day(step.get("at")) : day(rma.get("requestedAt"));
    var note = step.get("note");
    var inspection = (Map<?, ?>) rma.get("inspection");
    var reference = rma.get("reference");
    var next =
        switch (status) {
          case "REQUESTED" -> "is requested, and waits for Staff to decide on it.";
          case "APPROVED" ->
              "is approved. Please send it back by %s, with %s on the parcel, to %s."
                  .formatted(day(rma.get("sendBackBy")), reference, returnAddress(rma));
          case "REJECTED" -> "was rejected on %s%s.".formatted(on, because(note));
          case "RECEIVED" -> "was received on %s, and is inspected next.".formatted(on);
          case "EXPIRED" ->
              "expired on %s: its Send-back deadline, %s, passed before it reached us."
                  .formatted(on, day(rma.get("sendBackBy")));
          case "INSPECTED" ->
              "was inspected on %s (%s), and is resolved next."
                  .formatted(on, word(inspection == null ? null : inspection.get("outcome")));
          case "REFUNDED" ->
              "was refunded on %s: %s. Your bank may take a few days to show it."
                  .formatted(on, money(rma.get("refundAmount")));
          case "EXCHANGED" ->
              "was exchanged on %s: a replacement is on its way, and its page tracks it."
                  .formatted(on);
          case "CLOSED" ->
              "was closed on %s%s."
                  .formatted(
                      on,
                      because(note == null && inspection != null ? inspection.get("note") : note));
          case "ON_HOLD" ->
              "is on hold since %s%s. Staff will look at it.".formatted(on, because(note));
          default -> "is %s.".formatted(word(status));
        };
    return "Your RMA %s, %s on Order %s, %s"
        .formatted(reference, claim(rma), rma.get("orderReference"), next);
  }

  /** "a return of 1 Pixel 9", or "a warranty claim of …". */
  private static String claim(Map<String, Object> rma) {
    return "%s of %s %s"
        .formatted(
            "WARRANTY_CLAIM".equals(rma.get("kind")) ? "a warranty claim" : "a return",
            rma.get("quantity"),
            rma.get("productName"));
  }

  /**
   * "On Order #7F1C2A3B, you can return the Pixel 9 until 6 December 2026, and claim under its
   * warranty until 6 November 2028. To request a return or a warranty claim, open Order #7F1C2A3B's
   * page.": each line's windows, open or ended as of {@code now}.
   */
  private static String eligibility(
      Map<String, Object> eligibility, Optional<String> named, Instant now) {
    var order = eligibility.get("orderReference");
    var clauses = new ArrayList<String>();
    var claimable = false;
    for (var line : list(eligibility.get("lines"))) {
      var product = line.get("productName");
      if (((Number) line.get("unitsFree")).intValue() == 0) {
        clauses.add("every %s is already in an RMA".formatted(product));
        continue;
      }
      var returnEnds = Instant.parse(String.valueOf(line.get("returnPeriodEndsAt")));
      var warrantyEnds =
          line.get("warrantyWindowEndsAt") == null
              ? null
              : Instant.parse(String.valueOf(line.get("warrantyWindowEndsAt")));
      var returnable = now.isBefore(returnEnds);
      var underWarranty = warrantyEnds != null && now.isBefore(warrantyEnds);
      claimable |= returnable || underWarranty;
      var warranty =
          warrantyEnds == null
              ? ", and it has no warranty"
              : underWarranty
                  ? (returnable ? ", and" : ", but you can")
                      + " claim under its warranty until "
                      + DAY.format(warrantyEnds)
                  : (returnable
                          ? ", and its Warranty Window ended on "
                          : ", and its Warranty Window on ")
                      + DAY.format(warrantyEnds);
      clauses.add(
          (returnable ? "you can return the %s until %s" : "the %s's Return Period ended on %s")
                  .formatted(product, DAY.format(returnEnds))
              + warranty);
    }
    var reply =
        (named.isPresent() ? "On Order %s, " : "On your latest Order, %s, ").formatted(order)
            + String.join("; ", clauses)
            + ".";
    return claimable
        ? reply + " To request a return or a warranty claim, open Order %s's page.".formatted(order)
        : reply;
  }

  /** "Ecomm Returns Berlin, Lagerweg 3, 12099 Berlin, DE", or where to find it. */
  private static String returnAddress(Map<String, Object> rma) {
    if (!(rma.get("returnAddress") instanceof Map<?, ?> address) || !address.containsKey("name")) {
      return "the Return address on its page";
    }
    return "%s, %s, %s %s, %s"
        .formatted(
            address.get("name"),
            address.get("street"),
            address.get("postcode"),
            address.get("city"),
            address.get("country"));
  }

  /**
   * "I found 2 Products for "noise cancelling headphones": the Sony WH-1000XM5 at 379.00 EUR, in
   * Stock; and the Bose QuietComfort Ultra Headphones from 449.00 EUR, out of Stock."
   */
  private static String found(String query, List<Map<String, Object>> products) {
    var each =
        products.stream()
            .map(
                p ->
                    "the %s %s %s, %s"
                        .formatted(
                            p.get("name"),
                            Boolean.TRUE.equals(p.get("priceVaries")) ? "from" : "at",
                            money(p.get("priceFrom")),
                            inStock(p.get("inStock"))))
            .toList();
    return "I found %d Product%s for \"%s\": %s."
        .formatted(
            products.size(),
            products.size() == 1 ? "" : "s",
            query,
            each.size() == 1
                ? each.getFirst()
                : String.join("; ", each.subList(0, each.size() - 1)) + "; and " + each.getLast());
  }

  /**
   * "The Google Pixel 9 is out of Stock in Obsidian, 256 GB at 899.00 EUR.": the Variants whose
   * axis values the message names most of, or else every one, in Stock first.
   */
  private static String stock(Map<String, Object> product, String message) {
    var said = message.toLowerCase(Locale.ROOT);
    var variants = list(product.get("variants"));
    var named =
        variants.stream()
            .collect(
                Collectors.groupingBy(
                    v ->
                        axisValues(v).stream()
                            .filter(
                                value ->
                                    Pattern.compile(
                                            "(?<![0-9a-z])"
                                                + Pattern.quote(value.toLowerCase(Locale.ROOT))
                                                + "(?![0-9a-z])")
                                        .matcher(said)
                                        .find())
                            .count(),
                    TreeMap::new,
                    Collectors.toList()))
            .lastEntry()
            .getValue();
    var clauses = new ArrayList<String>();
    for (var inStock : List.of(true, false)) {
      var each =
          named.stream()
              .filter(v -> inStock.equals(v.get("inStock")))
              .map(
                  v -> {
                    var axes = String.join(", ", axisValues(v));
                    return (axes.isEmpty() ? "at " : axes + " at ") + money(v.get("price"));
                  })
              .toList();
      if (!each.isEmpty()) {
        var first = each.getFirst().startsWith("at ") ? " " : " in ";
        clauses.add(inStock(inStock) + first + sentence(each));
      }
    }
    return "The %s is %s.".formatted(product.get("name"), String.join(", and ", clauses));
  }

  /**
   * "The Google Pixel 9 comes with Google's 24-month warranty, counted from the day it is
   * delivered.", or that it has none.
   */
  private static String warrantyTerms(Map<String, Object> product) {
    if (!(product.get("warranty") instanceof Map<?, ?> warranty)) {
      return "The %s has no warranty.".formatted(product.get("name"));
    }
    var whose = warranty.get("manufacturer") == null ? "a" : warranty.get("manufacturer") + "'s";
    return "The %s comes with %s %s-month warranty, counted from the day it is delivered."
        .formatted(product.get("name"), whose, warranty.get("months"));
  }

  /** A Variant's axis values, in its axes' order: "Obsidian", "256 GB". */
  private static List<String> axisValues(Map<String, Object> variant) {
    return variant.get("axisValues") instanceof Map<?, ?> values
        ? values.values().stream().map(String::valueOf).toList()
        : List.of();
  }

  private static String inStock(Object inStock) {
    return Boolean.TRUE.equals(inStock) ? "in Stock" : "out of Stock";
  }

  /** The words of {@code text}, in lower case. */
  private static Set<String> words(String text) {
    return WORD.matcher(text.toLowerCase(Locale.ROOT))
        .results()
        .map(MatchResult::group)
        .collect(Collectors.toSet());
  }

  /** ": the reason", if there is one. */
  private static String because(Object note) {
    return note == null ? "" : ": " + note;
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

  /** A status as a sentence reads it: {@code PAID} as "paid", {@code ON_HOLD} as "on hold". */
  private static String word(Object status) {
    return switch (String.valueOf(status)) {
      case "FULFILLED" -> "packed";
      default -> String.valueOf(status).replace('_', ' ').toLowerCase(Locale.ROOT);
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
