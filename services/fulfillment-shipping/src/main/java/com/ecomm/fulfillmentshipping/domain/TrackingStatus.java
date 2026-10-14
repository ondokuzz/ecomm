package com.ecomm.fulfillmentshipping.domain;

/** What a Tracking event says of a parcel the Carrier has. Only DELIVERED moves a Shipment on. */
public enum TrackingStatus {
  IN_TRANSIT,
  OUT_FOR_DELIVERY,
  DELIVERED
}
