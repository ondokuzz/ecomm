package com.ecomm.orchestration.application;

import com.ecomm.orchestration.application.port.in.ReturnsRequest;
import com.ecomm.orchestration.application.port.in.ReturnsUseCase;
import com.ecomm.orchestration.application.port.in.RmaChange;
import com.ecomm.orchestration.application.port.in.ShipmentChange;
import com.ecomm.orchestration.application.port.out.ReturnsWorkflows;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts an RMA's returns Saga when its {@code APPROVED} event comes, and passes its later events
 * and its replacement Shipment's on to it. A start refused because the RMA had one already, or a
 * signal with no returns Saga to wake, is expected: events are delivered at least once, and
 * replayed.
 */
public class ReturnsService implements ReturnsUseCase {

  private static final Logger log = LoggerFactory.getLogger(ReturnsService.class);

  private final ReturnsWorkflows workflows;
  private final Duration inspectionCheck;
  private final Duration packingCheck;

  /**
   * {@code inspectionCheck}: how long each returns Saga awaits an Inspection before it reads the
   * RMA again; {@code packingCheck}: how long it awaits its replacement's packing before it reads
   * the replacement again.
   */
  public ReturnsService(
      ReturnsWorkflows workflows, Duration inspectionCheck, Duration packingCheck) {
    this.workflows = workflows;
    this.inspectionCheck = inspectionCheck;
    this.packingCheck = packingCheck;
  }

  @Override
  public void rmaChanged(RmaChanged rma) {
    if ("APPROVED".equals(rma.status())) {
      var started =
          workflows.start(
              new ReturnsRequest(
                  rma.rmaId(),
                  rma.reference(),
                  rma.orderId(),
                  rma.customerId(),
                  rma.variantId(),
                  rma.quantity(),
                  rma.kind(),
                  rma.refundAmount(),
                  rma.sendBackBy(),
                  inspectionCheck,
                  packingCheck,
                  rma.correlationId()));
      if (started) {
        log.info("Started {}'s returns Saga", rma.reference());
        return;
      }
    }
    if (workflows.signal(rma.rmaId(), new RmaChange(rma.version(), rma.status()))) {
      log.info(
          "Signalled {}'s returns Saga: version {} is {}",
          rma.reference(),
          rma.version(),
          rma.status());
    } else {
      log.info(
          "Skipped {} version {} ({}): no returns Saga is running for it",
          rma.reference(),
          rma.version(),
          rma.status());
    }
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
