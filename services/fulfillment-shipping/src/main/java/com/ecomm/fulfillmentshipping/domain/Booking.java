package com.ecomm.fulfillmentshipping.domain;

/** A Carrier's acceptance of a Shipment: who carries it, and the tracking number they gave it. */
public record Booking(String carrier, String trackingNumber) {}
