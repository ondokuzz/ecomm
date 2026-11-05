package com.ecomm.supportassistant.domain;

import java.util.Locale;

/**
 * A record a reply names, for the Storefront to link to its page.
 *
 * @param id its ID, which its page is found by
 * @param reference how a Customer knows it, such as the Order reference {@code #EA2CA751}, or a
 *     Product's SKU
 * @param label how the link reads
 */
public record Reference(Kind kind, String id, String reference, String label) {

  public enum Kind {
    ORDER,
    RMA,
    PRODUCT
  }

  /** An Order, by its Order reference: the first eight hex digits of its ID, in upper case. */
  public static Reference order(String orderId) {
    var reference = orderReference(orderId);
    return new Reference(Kind.ORDER, orderId, reference, "Order " + reference);
  }

  /** An RMA, by its RMA reference, such as {@code RMA-3F2A9C1B}. */
  public static Reference rma(String rmaId, String reference) {
    return new Reference(Kind.RMA, rmaId, reference, reference);
  }

  /** A Product, by its SKU, which its page is found by; its link reads its name. */
  public static Reference product(String sku, String name) {
    return new Reference(Kind.PRODUCT, sku, sku, name);
  }

  /** The Order reference an Order's emails and pages show, such as {@code #EA2CA751}. */
  public static String orderReference(String orderId) {
    return "#" + orderId.substring(0, 8).toUpperCase(Locale.ROOT);
  }
}
