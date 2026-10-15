package com.ecomm.returnswarranty.domain;

import java.util.List;

/**
 * What a Customer asks for in an RMA: units of one Variant of their delivered Order, by Serial
 * number for a serial-tracked Variant, to return or to claim the warranty on, and why.
 */
public record RmaRequest(
    String customerId,
    String orderId,
    String variantId,
    int quantity,
    List<String> serialNumbers,
    RmaKind kind,
    String reason) {

  public RmaRequest {
    serialNumbers = List.copyOf(serialNumbers);
  }
}
