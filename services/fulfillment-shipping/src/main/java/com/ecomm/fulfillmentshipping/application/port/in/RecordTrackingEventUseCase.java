package com.ecomm.fulfillmentshipping.application.port.in;

import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.TrackingEvent;
import java.util.Optional;

public interface RecordTrackingEventUseCase {

  /**
   * The Shipment after the event, and whether the event was new: a repeat of a recorded event ID
   * changes nothing.
   */
  record Recorded(Shipment shipment, boolean isNew) {}

  /**
   * Records {@code event} for the Shipment the Carrier knows by {@code trackingNumber}; empty when
   * no Shipment has it.
   */
  Optional<Recorded> record(String trackingNumber, TrackingEvent event);
}
