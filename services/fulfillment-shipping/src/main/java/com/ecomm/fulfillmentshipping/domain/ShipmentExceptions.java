package com.ecomm.fulfillmentshipping.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Why a Shipment command is refused. */
public final class ShipmentExceptions {

  private ShipmentExceptions() {}

  /** A Shipment asked to move to a status its state machine doesn't allow from where it is. */
  public static class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(UUID shipmentId, ShipmentStatus from, ShipmentStatus to) {
      super("Shipment " + shipmentId + " is " + from + ", so it can't become " + to);
    }
  }

  /** Serial numbers entered to pack a Shipment that are malformed, miscounted or repeated. */
  public static class InvalidSerialNumbersException extends RuntimeException {

    private final List<FieldViolation> violations;

    public InvalidSerialNumbersException(List<FieldViolation> violations) {
      super("The Serial numbers are invalid: " + violations);
      this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
      return violations;
    }
  }

  /** A Serial number entered to pack a Shipment that another Shipment already carries. */
  public static class SerialNumberAlreadyShippedException extends RuntimeException {

    private final String serialNumber;

    public SerialNumberAlreadyShippedException(String serialNumber) {
      super("Serial number " + serialNumber + " has already shipped on another Shipment");
      this.serialNumber = serialNumber;
    }

    public String serialNumber() {
      return serialNumber;
    }
  }

  /** An Order already has its Shipment: one Order has one. */
  public static class ShipmentExistsException extends RuntimeException {

    public ShipmentExistsException(String orderId) {
      super("Order " + orderId + " already has a Shipment");
    }
  }

  /**
   * Variants the Product projection doesn't hold yet, so a Shipment of them can't name their
   * Products or know their Serial tracking. Catalog's events catch up, so a later try succeeds.
   */
  public static class ProductsNotKnownYetException extends RuntimeException {

    public ProductsNotKnownYetException(Collection<String> variantIds) {
      super("No Product is known yet for Variants " + variantIds);
    }
  }
}
