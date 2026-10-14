package com.ecomm.fulfillmentshipping.domain;

import java.util.List;

/**
 * A quantity of one Variant in a Shipment, with its Product's name and Serial tracking as Catalog
 * had them when the Shipment was created.
 *
 * @param serialNumbers one per unit once packed, for a serial-tracked Variant; empty otherwise
 */
public record ShipmentLine(
    String variantId,
    String productName,
    int quantity,
    SerialTracking serialTracking,
    List<String> serialNumbers) {

  public ShipmentLine {
    serialNumbers = List.copyOf(serialNumbers);
  }

  ShipmentLine withSerialNumbers(List<String> serialNumbers) {
    return new ShipmentLine(variantId, productName, quantity, serialTracking, serialNumbers);
  }
}
