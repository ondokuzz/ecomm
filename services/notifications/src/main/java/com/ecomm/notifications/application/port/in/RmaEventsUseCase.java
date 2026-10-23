package com.ecomm.notifications.application.port.in;

import java.time.Instant;
import java.util.List;

/** Records the Notifications an RMA's event triggers. */
public interface RmaEventsUseCase {

  /**
   * An RMA's event: why it was published, and the RMA then. {@code kind} is {@code RETURN} or
   * {@code WARRANTY_CLAIM}. {@code sendBackBy} and {@code warehouseId} are {@code null} until it is
   * approved, {@code warehouseId} also while that isn't known; {@code rejectionReason} is the
   * rejection's note and {@code inspectionNote} the Inspection's, each {@code null} without one.
   */
  record RmaChanged(
      String eventId,
      Instant occurredAt,
      String change,
      String rmaId,
      String orderId,
      String customerId,
      String kind,
      String productName,
      int quantity,
      Instant sendBackBy,
      String warehouseId,
      String rejectionReason,
      String inspectionNote) {}

  /** The Notifications recorded for it: none for a change that triggers nothing, or a repeat. */
  List<String> rmaChanged(RmaChanged event);
}
