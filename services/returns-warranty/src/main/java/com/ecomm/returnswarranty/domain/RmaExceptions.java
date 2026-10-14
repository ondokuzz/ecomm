package com.ecomm.returnswarranty.domain;

import java.util.UUID;

/** Why an RMA is refused, or a decision on one. */
public final class RmaExceptions {

  private RmaExceptions() {}

  /** More units than were delivered, less those the Customer's other RMAs for them hold. */
  public static class QuantityExceededException extends RuntimeException {

    private final int available;

    public QuantityExceededException(String variantId, int available) {
      super("Only " + available + " of Variant " + variantId + " can be claimed");
      this.available = available;
    }

    public int available() {
      return available;
    }
  }

  /** Not one Serial number per unit of a serial-tracked Variant, or any for an untracked one. */
  public static class SerialNumbersMiscountedException extends RuntimeException {

    private final int expected;

    public SerialNumbersMiscountedException(int expected) {
      super("The RMA must name " + expected + " Serial numbers, one per unit");
      this.expected = expected;
    }

    public int expected() {
      return expected;
    }
  }

  /** A Serial number that didn't ship on the Order for that Variant. */
  public static class SerialNumberNotShippedException extends RuntimeException {

    private final String serialNumber;

    public SerialNumberNotShippedException(String serialNumber) {
      super("Serial number " + serialNumber + " wasn't shipped on this Order for this Variant");
      this.serialNumber = serialNumber;
    }

    public String serialNumber() {
      return serialNumber;
    }
  }

  /** A Serial number already in another requested or approved RMA, or named twice. */
  public static class SerialNumberAlreadyClaimedException extends RuntimeException {

    private final String serialNumber;

    public SerialNumberAlreadyClaimedException(String serialNumber) {
      super("Serial number " + serialNumber + " is already in an RMA");
      this.serialNumber = serialNumber;
    }

    public String serialNumber() {
      return serialNumber;
    }
  }

  /** A request after its Return Period or Warranty Window ended. */
  public static class OutsideWindowException extends RuntimeException {

    private final Window window;

    public OutsideWindowException(Window window) {
      super(
          (window.kind() == Window.Kind.RETURN_PERIOD ? "The Return Period" : "The Warranty Window")
              + " ended at "
              + window.endsAt());
      this.window = window;
    }

    public Window window() {
      return window;
    }
  }

  /** A warranty claim on a Variant whose Product has no Warranty terms. */
  public static class NoWarrantyException extends RuntimeException {

    public NoWarrantyException(String variantId) {
      super("Variant " + variantId + " has no manufacturer's warranty");
    }
  }

  /**
   * An Order with no delivered Shipment of the Customer's: not theirs, not delivered yet, or not
   * projected yet.
   */
  public static class NotDeliveredException extends RuntimeException {

    public NotDeliveredException(String orderId) {
      super("No delivered Order " + orderId);
    }
  }

  /**
   * A warranty claim on a Variant whose Product the projection doesn't hold yet, so its Warranty
   * terms aren't known. Catalog's events catch up, so a later try succeeds.
   */
  public static class ProductNotKnownYetException extends RuntimeException {

    public ProductNotKnownYetException(String variantId) {
      super("No Product is known yet for Variant " + variantId);
    }
  }

  /** A decision on an RMA that has already been decided. */
  public static class RmaAlreadyDecidedException extends RuntimeException {

    public RmaAlreadyDecidedException(UUID id, RmaStatus status) {
      super("RMA " + id + " is already " + status);
    }
  }
}
