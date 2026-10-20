package com.ecomm.orchestration.application.port.out;

import com.ecomm.orchestration.application.port.in.ReturnsRequest;
import com.ecomm.orchestration.application.port.in.RmaChange;
import com.ecomm.orchestration.application.port.in.ShipmentChange;

/** The returns workflows on Temporal: started once per RMA, and signalled. */
public interface ReturnsWorkflows {

  /**
   * Starts the RMA's returns Saga; false, starting nothing, when the RMA has had one already,
   * running or closed.
   */
  boolean start(ReturnsRequest request);

  /** Signals the RMA's returns Saga with its change; false when none is running. */
  boolean signal(String rmaId, RmaChange change);

  /** Signals the RMA's returns Saga with its replacement's change; false when none is running. */
  boolean signal(String rmaId, ShipmentChange change);
}
