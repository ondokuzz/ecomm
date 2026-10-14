package com.ecomm.fulfillmentshipping.domain;

import java.time.Instant;

/**
 * An entry of a Shipment's status history.
 *
 * @param by who moved it there: a Staff member's ID, {@link #ORCHESTRATION} or {@link #CARRIER}
 */
public record StatusChange(ShipmentStatus status, Instant at, String by) {

  /** The fulfillment Saga, for the changes it makes with Orchestration's identity. */
  public static final String ORCHESTRATION = "ORCHESTRATION";

  /** The Carrier, for a delivery its Carrier webhook reported. */
  public static final String CARRIER = "CARRIER";
}
