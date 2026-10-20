package com.ecomm.fulfillmentshipping.domain;

/**
 * What a Shipment is for: an Order's units ({@code ORDER}, one per Order), or a replacement for an
 * RMA's faulty units ({@code REPLACEMENT}, one per RMA), which nothing captures a Payment for.
 */
public enum ShipmentKind {
  ORDER,
  REPLACEMENT
}
