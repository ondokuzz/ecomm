package com.ecomm.notifications.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What an RMA's messages say it is about: its Order, its kind ({@code RETURN} or {@code
 * WARRANTY_CLAIM}), and how many units of which Product.
 */
public record RmaSummary(
    String orderId,
    String rmaId,
    String customerId,
    String kind,
    String productName,
    int quantity) {

  /**
   * {@code rma.reference} and {@code rma.kind} (such as {@code return}), {@code order.reference},
   * and {@code units}, such as {@code 1 × Pixel 9}.
   */
  LinkedHashMap<String, Object> model() {
    var model = new LinkedHashMap<String, Object>();
    model.put(
        "rma",
        Map.of(
            "reference",
            Formats.rmaReference(rmaId),
            "kind",
            "WARRANTY_CLAIM".equals(kind) ? "warranty claim" : "return"));
    model.put("order", Map.of("reference", Formats.orderReference(orderId)));
    model.put("units", quantity + " × " + productName);
    return model;
  }

  static RmaSummary sample(String kind) {
    return new RmaSummary(
        "3f2a9c1b-7d4e-4f0a-9b8c-2d1e0f3a4b5c",
        "7c4b2e1a-9f3d-4a8b-b6c5-0d1e2f3a4b5c",
        "sample-customer",
        kind,
        "Google Pixel 9 (128 GB, Obsidian)",
        1);
  }
}
