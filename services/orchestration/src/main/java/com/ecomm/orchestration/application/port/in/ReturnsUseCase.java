package com.ecomm.orchestration.application.port.in;

/** What Orchestration's consumers do for an RMA's returns Saga with the events they read. */
public interface ReturnsUseCase {

  /**
   * Wakes the RMA's returns Saga with its replacement Shipment's change. With no returns Saga
   * running for the RMA, it is skipped.
   */
  void replacementChanged(String rmaId, ShipmentChange change);
}
