package com.ecomm.orchestration.application.port.in;

/**
 * A Shipment event, as the fulfillment Saga is signalled with it: the Shipment, its version and the
 * status it was in at that version, such as {@code PACKED} or {@code DELIVERED}.
 */
public record ShipmentChange(String shipmentId, long version, String status) {}
