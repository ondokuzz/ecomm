package com.ecomm.fulfillmentshipping.application.port.in;

import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.IllegalTransitionException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.InvalidSerialNumbersException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.SerialNumberAlreadyShippedException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Moves a Shipment on. Each answers the changed Shipment, or empty for an unknown one, and throws
 * {@link IllegalTransitionException} for a move its status doesn't allow.
 */
public interface ChangeShipmentUseCase {

  Optional<Shipment> pick(UUID id, String staffId);

  /**
   * @param serialNumbers one per unit of each serial-tracked line, by Variant ID
   * @throws InvalidSerialNumbersException when one is malformed, a count is wrong, or one repeats
   * @throws SerialNumberAlreadyShippedException when another Shipment carries one
   */
  Optional<Shipment> pack(UUID id, Map<String, List<String>> serialNumbers, String staffId);

  /** Books it with the Carrier, passing {@code idempotencyKey} on, and ships it. */
  Optional<Shipment> dispatch(UUID id, String idempotencyKey);

  Optional<Shipment> hold(UUID id, String reason);
}
