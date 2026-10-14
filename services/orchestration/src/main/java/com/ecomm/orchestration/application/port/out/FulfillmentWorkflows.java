package com.ecomm.orchestration.application.port.out;

import com.ecomm.orchestration.application.port.in.FulfillmentRequest;
import com.ecomm.orchestration.application.port.in.ShipmentChange;

/** The fulfillment workflows on Temporal: started once per Order, and signalled. */
public interface FulfillmentWorkflows {

  /**
   * Starts the Order's fulfillment; false, starting nothing, when the Order has had one already,
   * running or closed.
   */
  boolean start(FulfillmentRequest request);

  /** Signals the Order's fulfillment; false when none is running for it. */
  boolean signal(String orderId, ShipmentChange change);
}
