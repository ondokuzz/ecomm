package com.ecomm.orchestration.application;

import com.ecomm.orchestration.application.port.in.ReturnsUseCase;
import com.ecomm.orchestration.application.port.in.ShipmentChange;
import com.ecomm.orchestration.application.port.out.ReturnsWorkflows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Passes an RMA's replacement Shipment events on to its returns Saga. A signal with no returns Saga
 * to wake is expected: events are delivered at least once, and replayed.
 */
public class ReturnsService implements ReturnsUseCase {

  private static final Logger log = LoggerFactory.getLogger(ReturnsService.class);

  private final ReturnsWorkflows workflows;

  public ReturnsService(ReturnsWorkflows workflows) {
    this.workflows = workflows;
  }

  @Override
  public void replacementChanged(String rmaId, ShipmentChange change) {
    if (workflows.signal(rmaId, change)) {
      log.info(
          "Signalled RMA {}'s returns: replacement Shipment {} version {} is {}",
          rmaId,
          change.shipmentId(),
          change.version(),
          change.status());
    } else {
      log.info(
          "Skipped replacement Shipment {} version {}: no returns Saga is running for RMA {}",
          change.shipmentId(),
          change.version(),
          rmaId);
    }
  }
}
