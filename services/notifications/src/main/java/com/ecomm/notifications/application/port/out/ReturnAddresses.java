package com.ecomm.notifications.application.port.out;

import com.ecomm.notifications.domain.ReturnAddress;
import java.util.Optional;

/** The Return addresses of the Warehouses, as Fulfillment & Shipping configures them. */
public interface ReturnAddresses {

  /**
   * The Warehouse's Return address; empty for a Warehouse that isn't configured.
   *
   * @throws WarehousesUnavailableException when Fulfillment & Shipping can't answer now
   */
  Optional<ReturnAddress> of(String warehouseId);

  /** Fulfillment & Shipping didn't answer; asking again later may work. */
  class WarehousesUnavailableException extends RuntimeException {

    public WarehousesUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
