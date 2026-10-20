package com.ecomm.orchestration.application.port.in;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import java.time.Instant;

/** What Orchestration's consumers do for an RMA's returns Saga with the events they read. */
public interface ReturnsUseCase {

  /**
   * An RMA event: the RMA as it now stands, and the Correlation ID the event carried. {@code
   * refundAmount} and {@code sendBackBy} are null where the RMA has none yet.
   */
  record RmaChanged(
      String rmaId,
      long version,
      String status,
      String reference,
      String orderId,
      String customerId,
      String variantId,
      int quantity,
      String kind,
      Amount refundAmount,
      Instant sendBackBy,
      String correlationId) {}

  /**
   * Starts the RMA's returns Saga once it is {@code APPROVED}, at most once however often its
   * events are delivered; wakes it with any later event. With no returns Saga running for the RMA,
   * a later event is skipped.
   */
  void rmaChanged(RmaChanged rma);

  /**
   * Wakes the RMA's returns Saga with its replacement Shipment's change. With no returns Saga
   * running for the RMA, it is skipped.
   */
  void replacementChanged(String rmaId, ShipmentChange change);
}
