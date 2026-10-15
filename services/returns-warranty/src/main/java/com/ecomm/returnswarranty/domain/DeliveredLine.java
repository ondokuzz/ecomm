package com.ecomm.returnswarranty.domain;

import java.util.List;

/**
 * One line of a delivered Shipment: a Variant, its Product's name, how many units, and the Serial
 * number of each unit of a serial-tracked Variant, as Staff entered them when they packed it.
 */
public record DeliveredLine(
    String variantId,
    String productName,
    int quantity,
    SerialTracking serialTracking,
    List<String> serialNumbers) {

  public DeliveredLine {
    serialNumbers = List.copyOf(serialNumbers);
  }
}
