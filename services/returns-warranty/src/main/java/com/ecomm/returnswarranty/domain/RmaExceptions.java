package com.ecomm.returnswarranty.domain;

import java.util.List;
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

  /** A Serial number already in another RMA that holds its units, or named twice. */
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

  /** A step its status doesn't allow, such as receiving an RMA that isn't {@code APPROVED}. */
  public static class TransitionRefusedException extends RuntimeException {

    private final RmaStatus status;

    public TransitionRefusedException(UUID id, RmaStatus status, RmaStatus to) {
      this("RMA " + id + " is " + status + ", so it can't be " + to, status);
    }

    TransitionRefusedException(String message, RmaStatus status) {
      super(message);
      this.status = status;
    }

    /** The status it is in. */
    public RmaStatus status() {
      return status;
    }
  }

  /** A decision on an RMA that has already been decided. */
  public static class RmaAlreadyDecidedException extends TransitionRefusedException {

    public RmaAlreadyDecidedException(UUID id, RmaStatus status) {
      super("RMA " + id + " is already " + status, status);
    }
  }

  /**
   * An Order Management Order that the projection doesn't hold yet, so an RMA's Refund amount can't
   * be worked out. Its events catch up, so a later try succeeds.
   */
  public static class OrderNotKnownYetException extends RuntimeException {

    public OrderNotKnownYetException(String orderId) {
      super("Order " + orderId + " isn't known yet");
    }
  }

  /** Units received with Serial numbers that aren't the RMA's, each named. */
  public static class SerialMismatchException extends RuntimeException {

    private final List<String> serialNumbers;

    public SerialMismatchException(List<String> serialNumbers) {
      super("Serial numbers " + serialNumbers + " aren't this RMA's");
      this.serialNumbers = List.copyOf(serialNumbers);
    }

    public List<String> serialNumbers() {
      return serialNumbers;
    }
  }

  /** Units received that don't number the RMA's. */
  public static class QuantityMismatchException extends RuntimeException {

    private final int expected;

    public QuantityMismatchException(int expected) {
      super("The RMA is for " + expected + " units, each by Serial number where it has one");
      this.expected = expected;
    }

    public int expected() {
      return expected;
    }
  }

  /** An Inspection outcome for the other kind of RMA. */
  public static class OutcomeNotForKindException extends RuntimeException {

    public OutcomeNotForKindException(InspectionOutcome outcome, RmaKind kind) {
      super(outcome + " isn't an outcome for a " + kind);
    }
  }

  /**
   * A resolution the Inspection doesn't allow: a Refund or an exchange after a refusal, an exchange
   * of an accepted return, or closing what was accepted.
   */
  public static class ResolutionContradictsInspectionException extends RuntimeException {

    public ResolutionContradictsInspectionException(RmaStatus resolution, Inspection inspection) {
      super("An RMA inspected as " + inspection.outcome() + " can't be " + resolution);
    }
  }
}
