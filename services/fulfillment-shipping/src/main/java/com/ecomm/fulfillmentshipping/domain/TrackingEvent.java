package com.ecomm.fulfillmentshipping.domain;

import java.time.Instant;
import java.util.Comparator;

/**
 * Something the Carrier reports of a Shipment it has, by Carrier webhook.
 *
 * @param eventId the Carrier's ID for it, unique across its webhooks
 * @param occurredAt when it happened, as the Carrier says
 */
public record TrackingEvent(String eventId, TrackingStatus status, Instant occurredAt) {

  /** Oldest first: the order a Shipment's Tracking events are listed in. */
  public static final Comparator<TrackingEvent> IN_TIME_ORDER =
      Comparator.comparing(TrackingEvent::occurredAt).thenComparing(TrackingEvent::eventId);
}
