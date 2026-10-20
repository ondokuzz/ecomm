package com.ecomm.returnswarranty;

import com.networknt.schema.JsonSchema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only: Order Management's {@code order-management.order} events, as it publishes them, each
 * checked against the topic's schema before it is sent.
 */
final class Orders {

  static final String TOPIC = "order-management.order";
  static final String GROUP = "returns-warranty.orders";

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchema SCHEMA = Shipments.loadSchema(TOPIC);

  private Orders() {}

  /** An Order of the Customer's, with no lines, Discounts or Tax yet, in euros. */
  static Order order(String orderId, String customerId) {
    return new Order(orderId, customerId);
  }

  static final class Order {
    private final String orderId;
    private final String customerId;
    private final List<Map<String, Object>> lines = new ArrayList<>();
    private final List<Map<String, Object>> discounts = new ArrayList<>();
    private long subtotal;
    private long tax;
    private long version = 5;

    private Order(String orderId, String customerId) {
      this.orderId = orderId;
      this.customerId = customerId;
    }

    Order line(String variantId, int quantity, long unitPriceMinor) {
      lines.add(
          Map.of("variantId", variantId, "quantity", quantity, "unitPrice", money(unitPriceMinor)));
      subtotal += quantity * unitPriceMinor;
      return this;
    }

    Order coupon(long amountMinor) {
      discounts.add(Map.of("source", "COUPON", "amount", money(amountMinor), "couponCode", "C"));
      return this;
    }

    Order tax(long amountMinor) {
      tax = amountMinor;
      return this;
    }

    Order version(long version) {
      this.version = version;
      return this;
    }

    /** Publishes it {@code DELIVERED}, with 4.99 of Shipping that its total includes. */
    Order publish() {
      var discounted = discounts.stream().mapToLong(d -> amountOf(d)).sum();
      var shipping = 499;
      var order = new LinkedHashMap<String, Object>();
      order.put("customerId", customerId);
      order.put("status", "DELIVERED");
      order.put("placedAt", "2026-10-01T09:00:00Z");
      order.put("lines", lines);
      order.put("discounts", discounts);
      order.put("tax", money(tax));
      order.put("subtotal", money(subtotal));
      order.put("total", money(subtotal - discounted + tax + shipping));
      order.put(
          "shippingOption", Map.of("id", "STANDARD", "carrier", "DHL", "cost", money(shipping)));
      order.put(
          "statusHistory",
          List.of(
              Map.of("status", "PLACED", "at", "2026-10-01T09:00:00Z", "changedBy", "CHECKOUT")));
      var event = new LinkedHashMap<String, Object>();
      event.put("eventId", UUID.randomUUID().toString());
      event.put("occurredAt", Instant.now().toString());
      event.put("orderId", orderId);
      event.put("version", version);
      event.put("change", "STATUS_CHANGED");
      event.put("order", order);
      Shipments.send(TOPIC, SCHEMA, orderId, JSON.writeValueAsString(event));
      return this;
    }

    @SuppressWarnings("unchecked")
    private static long amountOf(Map<String, Object> discount) {
      return ((Number) ((Map<String, Object>) discount.get("amount")).get("amountMinor"))
          .longValue();
    }

    private static Map<String, Object> money(long amountMinor) {
      return Map.of("amountMinor", amountMinor, "currency", "EUR");
    }
  }
}
