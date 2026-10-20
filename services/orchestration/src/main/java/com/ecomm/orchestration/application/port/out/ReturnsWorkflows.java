package com.ecomm.orchestration.application.port.out;

import com.ecomm.orchestration.application.port.in.ShipmentChange;

/** The returns workflows on Temporal, one per RMA. */
public interface ReturnsWorkflows {

  /** Signals the RMA's returns Saga with its replacement's change; false when none is running. */
  boolean signal(String rmaId, ShipmentChange change);
}
