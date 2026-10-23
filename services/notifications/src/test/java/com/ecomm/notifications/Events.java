package com.ecomm.notifications;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only: the events Notifications consumes, as Order Management, Catalog, Fulfillment &
 * Shipping, Payment and Returns & Warranty publish them. Each is checked against its topic's schema
 * in {@code platform/event-schemas} before it is sent, so a test can't rely on an event no producer
 * could publish.
 */
final class Events {

  static final String ORDERS = "order-management.order";
  static final String PRODUCTS = "catalog.product";
  static final String SHIPMENTS = "fulfillment-shipping.shipment";
  static final String PAYMENTS = "payment.payment";
  static final String RMAS = "returns-warranty.rma";
  static final List<String> TOPICS = List.of(ORDERS, PRODUCTS, SHIPMENTS, PAYMENTS, RMAS);
  static final List<String> GROUPS =
      List.of(
          "notifications.orders",
          "notifications.products",
          "notifications.shipments",
          "notifications.payments",
          "notifications.rmas");

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchemaFactory SCHEMAS =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

  private Events() {}

  /** A new Order of the Customer's, placed now, with no lines yet. */
  static OrderEvent order(String customerId) {
    return new OrderEvent(UUID.randomUUID().toString(), customerId);
  }

  /** A new {@code ORDER} Shipment of the Customer's Order, with one line of Pixel 9s. */
  static ShipmentEvent shipment(String orderId, String customerId) {
    return new ShipmentEvent(orderId, customerId, null);
  }

  /** A new {@code REPLACEMENT} Shipment for the RMA of the Customer's Order. */
  static ShipmentEvent replacement(String orderId, String rmaId, String customerId) {
    return new ShipmentEvent(orderId, customerId, rmaId);
  }

  /** A new Payment of {@code amountMinor} euros for the Customer's Order, not yet published. */
  static PaymentEvent payment(String orderId, String customerId, long amountMinor) {
    return new PaymentEvent(orderId, customerId, amountMinor);
  }

  /** A new return of one unit of the Customer's Order, not yet published. */
  static RmaEvent rma(String orderId, String customerId) {
    return new RmaEvent(orderId, customerId);
  }

  /** A Product with one Variant per {@code variantIds}, each with no axis values. */
  static ProductEvent product(String sku, String name, String... variantIds) {
    return new ProductEvent(sku, name, List.of(variantIds));
  }

  static final class OrderEvent {
    private final String orderId;
    private final String customerId;
    private final List<Map<String, Object>> lines = new ArrayList<>();
    private final List<Map<String, Object>> history = new ArrayList<>();
    private final Instant placedAt = Instant.parse("2026-10-21T09:30:00Z");
    private String currency = "EUR";
    private long shippingMinor = 499;
    private long version;
    private String eventId;

    private OrderEvent(String orderId, String customerId) {
      this.orderId = orderId;
      this.customerId = customerId;
    }

    String orderId() {
      return orderId;
    }

    /** The Order's reference, as the Storefront shows it. */
    String reference() {
      return "#" + orderId.substring(0, 8).toUpperCase();
    }

    OrderEvent line(String variantId, int quantity, long unitPriceMinor) {
      lines.add(
          Map.of("variantId", variantId, "quantity", quantity, "unitPrice", money(unitPriceMinor)));
      return this;
    }

    OrderEvent currency(String currency) {
      this.currency = currency;
      return this;
    }

    /** Publishes the Order {@code PLACED}, as version 1. */
    OrderEvent placed() {
      return publish("PLACED", "PLACED");
    }

    /** Publishes the Order's change to {@code status}, at the next version. */
    OrderEvent changedTo(String status) {
      return publish("STATUS_CHANGED", status);
    }

    OrderEvent paid() {
      return changedTo("PAID");
    }

    /** Publishes the last event again, the same event ID included: a redelivery. */
    OrderEvent redelivered() {
      send(ORDERS, orderId, lastEvent);
      return this;
    }

    private Map<String, Object> lastEvent;

    private OrderEvent publish(String change, String status) {
      version++;
      eventId = UUID.randomUUID().toString();
      history.add(Map.of("status", status, "at", Instant.now().toString()));
      long subtotal =
          lines.stream()
              .mapToLong(
                  l ->
                      ((Number) ((Map<?, ?>) l.get("unitPrice")).get("amountMinor")).longValue()
                          * ((Number) l.get("quantity")).longValue())
              .sum();
      var order = new LinkedHashMap<String, Object>();
      order.put("customerId", customerId);
      order.put("status", status);
      order.put("placedAt", placedAt.toString());
      order.put("lines", lines);
      order.put("discounts", List.of());
      order.put("tax", money(0));
      order.put("subtotal", money(subtotal));
      order.put("total", money(subtotal + shippingMinor));
      order.put(
          "shippingAddress",
          Map.of(
              "recipientName",
              "Ada Lovelace",
              "line1",
              "1 Main St",
              "postcode",
              "10115",
              "city",
              "Berlin",
              "country",
              "DE"));
      order.put(
          "shippingOption",
          Map.of("id", "STANDARD", "carrier", "DHL", "cost", money(shippingMinor)));
      order.put("statusHistory", List.copyOf(history));
      var event = new LinkedHashMap<String, Object>();
      event.put("eventId", eventId);
      event.put("occurredAt", Instant.now().toString());
      event.put("orderId", orderId);
      event.put("version", version);
      event.put("change", change);
      event.put("order", order);
      lastEvent = event;
      send(ORDERS, orderId, event);
      return this;
    }

    private Map<String, Object> money(long amountMinor) {
      return Map.of("amountMinor", amountMinor, "currency", currency);
    }
  }

  static final class ProductEvent {
    private final String sku;
    private final String name;
    private final List<String> variantIds;
    private final Map<String, Map<String, String>> axisValues = new LinkedHashMap<>();
    private long version = 1;

    private ProductEvent(String sku, String name, List<String> variantIds) {
      this.sku = sku;
      this.name = name;
      this.variantIds = variantIds;
    }

    ProductEvent version(long version) {
      this.version = version;
      return this;
    }

    /** The Variant's value on a Variant axis, such as {@code storage} {@code 128 GB}. */
    ProductEvent axis(String variantId, String axis, String value) {
      axisValues.computeIfAbsent(variantId, v -> new LinkedHashMap<>()).put(axis, value);
      return this;
    }

    void publish() {
      var product = new LinkedHashMap<String, Object>();
      product.put("name", name);
      product.put("category", "phones");
      product.put("attributes", Map.of());
      product.put("images", List.of());
      product.put(
          "variants",
          variantIds.stream()
              .map(
                  id ->
                      Map.of(
                          "variantId",
                          id,
                          "axisValues",
                          axisValues.getOrDefault(id, Map.of()),
                          "price",
                          Map.of("amountMinor", 1000, "currency", "EUR"),
                          "images",
                          List.of()))
              .toList());
      product.put("removed", false);
      var event = new LinkedHashMap<String, Object>();
      event.put("eventId", UUID.randomUUID().toString());
      event.put("occurredAt", Instant.now().toString());
      event.put("sku", sku);
      event.put("version", version);
      event.put("change", version == 1 ? "CREATED" : "UPDATED");
      event.put("product", product);
      send(PRODUCTS, sku, event);
    }
  }

  static final class ShipmentEvent {
    private final String shipmentId = UUID.randomUUID().toString();
    private final String orderId;
    private final String customerId;
    private final String rmaId;
    private final List<Map<String, Object>> history = new ArrayList<>();
    private final Map<String, Object> lastEvent = new LinkedHashMap<>();
    private String carrier;
    private String trackingNumber;
    private long version;

    private ShipmentEvent(String orderId, String customerId, String rmaId) {
      this.orderId = orderId;
      this.customerId = customerId;
      this.rmaId = rmaId;
    }

    /** The RMA a replacement is for, or {@code null}. */
    String rmaId() {
      return rmaId;
    }

    /** Publishes it {@code CREATED}, awaiting pick. */
    ShipmentEvent created() {
      return publish("CREATED", "AWAITING_PICK");
    }

    ShipmentEvent changedTo(String status) {
      return publish(status, status);
    }

    /** Publishes it {@code SHIPPED}, booked with the Carrier under {@code trackingNumber}. */
    ShipmentEvent shipped(String carrier, String trackingNumber) {
      this.carrier = carrier;
      this.trackingNumber = trackingNumber;
      return publish("SHIPPED", "SHIPPED");
    }

    ShipmentEvent delivered() {
      return publish("DELIVERED", "DELIVERED");
    }

    /** Publishes the last event again, the same event ID included: a redelivery. */
    ShipmentEvent redelivered() {
      send(SHIPMENTS, shipmentId, lastEvent);
      return this;
    }

    private ShipmentEvent publish(String change, String status) {
      version++;
      var now = Instant.now().toString();
      history.add(Map.of("status", status, "at", now, "by", "ORCHESTRATION"));
      var shipment = new LinkedHashMap<String, Object>();
      shipment.put("kind", rmaId == null ? "ORDER" : "REPLACEMENT");
      if (rmaId != null) {
        shipment.put("rmaId", rmaId);
      }
      shipment.put("orderId", orderId);
      shipment.put("customerId", customerId);
      shipment.put("warehouseId", "WH-BER");
      shipment.put("status", status);
      shipment.put(
          "lines",
          List.of(
              Map.of(
                  "variantId",
                  "PHN-PIXEL-9-BLK-128",
                  "productName",
                  "Pixel 9",
                  "quantity",
                  2,
                  "serialTracking",
                  "NONE",
                  "serialNumbers",
                  List.of())));
      if (carrier != null) {
        shipment.put("carrier", carrier);
        shipment.put("trackingNumber", trackingNumber);
      }
      shipment.put("trackingEvents", List.of());
      if (status.equals("DELIVERED")) {
        shipment.put("deliveredAt", "2026-10-23T14:05:00Z");
      }
      shipment.put("statusHistory", List.copyOf(history));
      shipment.put("createdAt", history.getFirst().get("at"));
      lastEvent.clear();
      lastEvent.put("eventId", UUID.randomUUID().toString());
      lastEvent.put("occurredAt", now);
      lastEvent.put("shipmentId", shipmentId);
      lastEvent.put("version", version);
      lastEvent.put("change", change);
      lastEvent.put("shipment", shipment);
      send(SHIPMENTS, shipmentId, lastEvent);
      return this;
    }
  }

  static final class PaymentEvent {
    private final String paymentId = UUID.randomUUID().toString();
    private final String orderId;
    private final String customerId;
    private final long amountMinor;
    private final List<Map<String, Object>> transactions = new ArrayList<>();
    private final Map<String, Object> lastEvent = new LinkedHashMap<>();
    private long refundedMinor;

    private PaymentEvent(String orderId, String customerId, long amountMinor) {
      this.orderId = orderId;
      this.customerId = customerId;
      this.amountMinor = amountMinor;
    }

    PaymentEvent authorized() {
      transactions.add(transaction("AUTHORIZATION", amountMinor, "APPROVED", null));
      return publish("AUTHORIZED", "AUTHORIZED", null);
    }

    PaymentEvent declined() {
      transactions.add(transaction("AUTHORIZATION", amountMinor, "DECLINED", null));
      return publish("DECLINED", "DECLINED", "insufficient_funds");
    }

    PaymentEvent captured() {
      transactions.add(transaction("CAPTURE", amountMinor, "APPROVED", null));
      return publish("CAPTURED", "CAPTURED", null);
    }

    /** An approved Refund of {@code refundMinor}, for {@code reference} unless it is null. */
    PaymentEvent refunded(long refundMinor, String reference) {
      refundedMinor += refundMinor;
      transactions.add(transaction("REFUND", refundMinor, "APPROVED", reference));
      return publish(
          "REFUNDED", refundedMinor == amountMinor ? "REFUNDED" : "PARTIALLY_REFUNDED", null);
    }

    /** A Refund of {@code refundMinor} the gateway refused. */
    PaymentEvent refundDeclined(long refundMinor) {
      transactions.add(transaction("REFUND", refundMinor, "DECLINED", null));
      return publish("REFUND_DECLINED", (String) lastPayment().get("status"), null);
    }

    /** Publishes the last event again, the same event ID included: a redelivery. */
    PaymentEvent redelivered() {
      send(PAYMENTS, paymentId, lastEvent);
      return this;
    }

    private Map<?, ?> lastPayment() {
      return (Map<?, ?>) lastEvent.get("payment");
    }

    private Map<String, Object> transaction(
        String kind, long amount, String outcome, String reference) {
      var transaction = new LinkedHashMap<String, Object>();
      transaction.put("kind", kind);
      transaction.put("amount", Map.of("amountMinor", amount, "currency", "EUR"));
      transaction.put("outcome", outcome);
      transaction.put("gatewayReference", "mock-" + UUID.randomUUID());
      if (outcome.equals("DECLINED")) {
        transaction.put("declineReason", "insufficient_funds");
      }
      transaction.put("at", Instant.now().toString());
      transaction.put("backfilled", false);
      if (reference != null) {
        transaction.put("reference", reference);
      }
      return transaction;
    }

    private PaymentEvent publish(String change, String status, String declineReason) {
      var payment = new LinkedHashMap<String, Object>();
      payment.put("orderId", orderId);
      payment.put("customerId", customerId);
      payment.put("amount", Map.of("amountMinor", amountMinor, "currency", "EUR"));
      payment.put("status", status);
      if (declineReason != null) {
        payment.put("declineReason", declineReason);
      }
      payment.put("transactions", List.copyOf(transactions));
      lastEvent.clear();
      lastEvent.put("eventId", UUID.randomUUID().toString());
      lastEvent.put("occurredAt", Instant.now().toString());
      lastEvent.put("paymentId", paymentId);
      lastEvent.put("version", transactions.size());
      lastEvent.put("change", change);
      lastEvent.put("payment", payment);
      send(PAYMENTS, paymentId, lastEvent);
      return this;
    }
  }

  static final class RmaEvent {
    private final String rmaId = UUID.randomUUID().toString();
    private final String orderId;
    private final String customerId;
    private final List<Map<String, Object>> decisions = new ArrayList<>();
    private final Map<String, Object> lastEvent = new LinkedHashMap<>();
    private final Instant requestedAt = Instant.now();
    private String kind = "RETURN";
    private String warehouseId;
    private String sendBackBy;
    private Map<String, Object> inspection;
    private long version;

    private RmaEvent(String orderId, String customerId) {
      this.orderId = orderId;
      this.customerId = customerId;
    }

    String rmaId() {
      return rmaId;
    }

    /** Its RMA reference, such as {@code RMA-3F2A9C1B}. */
    String reference() {
      return "RMA-" + rmaId.substring(0, 8).toUpperCase();
    }

    /** A warranty claim rather than a return; before it is published. */
    RmaEvent warrantyClaim() {
      kind = "WARRANTY_CLAIM";
      return this;
    }

    RmaEvent requested() {
      return publish("REQUESTED", "REQUESTED", null);
    }

    /**
     * Approved, its units to go back to {@code warehouseId} (none if null) by {@code sendBackBy}.
     */
    RmaEvent approved(String warehouseId, String sendBackBy) {
      this.warehouseId = warehouseId;
      this.sendBackBy = sendBackBy;
      return publish("APPROVED", "APPROVED", null);
    }

    RmaEvent rejected(String reason) {
      return publish("REJECTED", "REJECTED", reason);
    }

    RmaEvent received() {
      return publish("RECEIVED", "RECEIVED", null);
    }

    RmaEvent expired() {
      return publish("EXPIRED", "EXPIRED", null);
    }

    /** Inspected with {@code outcome}, and {@code note} unless it is null. */
    RmaEvent inspected(String outcome, String note) {
      inspection = new LinkedHashMap<>();
      inspection.put("outcome", outcome);
      if (note != null) {
        inspection.put("note", note);
      }
      return publish("INSPECTED", "INSPECTED", note);
    }

    /** Resolved as {@code status}: {@code REFUNDED}, {@code EXCHANGED} or {@code CLOSED}. */
    RmaEvent resolved(String status) {
      return publish(status, status, null);
    }

    /** Publishes the last event again, the same event ID included: a redelivery. */
    RmaEvent redelivered() {
      send(RMAS, rmaId, lastEvent);
      return this;
    }

    private RmaEvent publish(String change, String status, String note) {
      version++;
      var now = Instant.now().toString();
      var decision = new LinkedHashMap<String, Object>();
      decision.put("status", status);
      decision.put("decidedBy", change.equals("REQUESTED") ? "CUSTOMER" : "staff-7");
      decision.put("decidedAt", now);
      if (note != null) {
        decision.put("note", note);
      }
      decisions.add(decision);
      var rma = new LinkedHashMap<String, Object>();
      rma.put("reference", reference());
      rma.put("orderId", orderId);
      rma.put("customerId", customerId);
      rma.put("variantId", "PHN-PIXEL-9-BLK-128");
      rma.put("productName", "Pixel 9");
      rma.put("quantity", 1);
      rma.put("serialNumbers", List.of("490154203237518"));
      rma.put("kind", kind);
      rma.put("reason", "Not what I expected.");
      var window = new LinkedHashMap<String, Object>();
      window.put("kind", kind.equals("RETURN") ? "RETURN_PERIOD" : "WARRANTY_WINDOW");
      window.put("startsAt", "2026-10-10T09:30:00Z");
      window.put("endsAt", "2026-10-24T09:30:00Z");
      if (kind.equals("WARRANTY_CLAIM")) {
        window.put("warranty", Map.of("months", 24, "manufacturer", "Google"));
      }
      rma.put("window", window);
      rma.put("status", status);
      rma.put("requestedAt", requestedAt.toString());
      rma.put("refundAmount", Map.of("amountMinor", 79900, "currency", "EUR"));
      if (sendBackBy != null) {
        rma.put("sendBackBy", sendBackBy);
      }
      if (warehouseId != null) {
        rma.put("warehouseId", warehouseId);
      }
      if (inspection != null) {
        rma.put("inspection", inspection);
      }
      if (List.of("REFUNDED", "EXCHANGED", "CLOSED").contains(status)) {
        rma.put("resolution", Map.of());
      }
      rma.put("decisions", List.copyOf(decisions));
      lastEvent.clear();
      lastEvent.put("eventId", UUID.randomUUID().toString());
      lastEvent.put("occurredAt", now);
      lastEvent.put("rmaId", rmaId);
      lastEvent.put("version", version);
      lastEvent.put("change", change);
      lastEvent.put("rma", rma);
      send(RMAS, rmaId, lastEvent);
      return this;
    }
  }

  private static void send(String topic, String key, Map<String, Object> event) {
    var json = JSON.writeValueAsString(event);
    var errors = schemaOf(topic).validate(json, InputFormat.JSON);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(topic + " event breaks its schema: " + errors);
    }
    Topics.send(topic, key, json, null);
  }

  private static JsonSchema schemaOf(String topic) {
    try (var in =
        Events.class.getClassLoader().getResourceAsStream("event-schemas/" + topic + ".json")) {
      return SCHEMAS.getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
