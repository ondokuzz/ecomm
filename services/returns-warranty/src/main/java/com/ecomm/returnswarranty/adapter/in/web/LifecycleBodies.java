package com.ecomm.returnswarranty.adapter.in.web;

import com.ecomm.returnswarranty.domain.InspectionOutcome;
import com.ecomm.returnswarranty.domain.RmaStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The bodies of the steps after an RMA decision, each checked for what it can be without the RMA.
 */
final class LifecycleBodies {

  private LifecycleBodies() {}

  /** The longest Payment transaction reference kept. */
  static final int MAX_REFERENCE = 255;

  /**
   * Units Staff received: {@code {"serialNumbers": [...]}} for a serial-tracked Variant, or {@code
   * {"quantity": n}} for any other; exactly one of them.
   */
  record ReceiveBody(List<String> serialNumbers, Integer quantity) {

    List<String> serials() {
      var given = serialNumbers == null ? List.<String>of() : serialNumbers;
      if (given.isEmpty() == (quantity == null)) {
        throw new InvalidRequestException(
            "serialNumbers", "or quantity must be given, and not both");
      }
      if (quantity != null && quantity < 1) {
        throw new InvalidRequestException("quantity", "must be a positive integer");
      }
      for (var i = 0; i < given.size(); i++) {
        if (given.get(i) == null || given.get(i).isBlank()) {
          throw new InvalidRequestException(
              "serialNumbers[" + i + "]", "must be a non-blank string");
        }
      }
      return given;
    }

    int units() {
      return quantity == null ? serialNumbers.size() : quantity;
    }
  }

  /** Staff's Inspection: {@code {"outcome", "note"?}}, the note required for a refusing outcome. */
  record InspectBody(String outcome, String note) {

    InspectionOutcome toOutcome() {
      InspectionOutcome parsed;
      try {
        parsed = InspectionOutcome.valueOf(outcome);
      } catch (IllegalArgumentException | NullPointerException e) {
        throw new InvalidRequestException(
            "outcome", "must be one of " + List.of(InspectionOutcome.values()));
      }
      if (parsed.refusing() || note != null) {
        RequestRmaBody.text("note", note);
      }
      return parsed;
    }
  }

  /**
   * The returns Saga's resolution: {@code {"resolution": "REFUNDED" | "EXCHANGED" | "CLOSED",
   * "paymentTransactionReference"?, "replacementShipmentId"?, "note"?}}: a Refund names its Payment
   * transaction, and an exchange its replacement Shipment.
   */
  record ResolveBody(
      String resolution,
      String paymentTransactionReference,
      String replacementShipmentId,
      String note) {

    private static final Set<RmaStatus> RESOLUTIONS =
        Set.of(RmaStatus.REFUNDED, RmaStatus.EXCHANGED, RmaStatus.CLOSED);

    RmaStatus toResolution() {
      RmaStatus parsed = null;
      try {
        parsed = RmaStatus.valueOf(resolution);
      } catch (IllegalArgumentException | NullPointerException e) {
        // Refused below.
      }
      if (!RESOLUTIONS.contains(parsed)) {
        throw new InvalidRequestException("resolution", "must be REFUNDED, EXCHANGED or CLOSED");
      }
      if (parsed == RmaStatus.REFUNDED
          && (paymentTransactionReference == null
              || paymentTransactionReference.isBlank()
              || paymentTransactionReference.length() > MAX_REFERENCE)) {
        throw new InvalidRequestException(
            "paymentTransactionReference",
            "must be 1 to " + MAX_REFERENCE + " characters for a Refund");
      }
      if (parsed == RmaStatus.EXCHANGED) {
        replacement();
      }
      if (note != null) {
        RequestRmaBody.text("note", note);
      }
      return parsed;
    }

    UUID replacement() {
      if (replacementShipmentId == null) {
        throw new InvalidRequestException(
            "replacementShipmentId", "must be the replacement Shipment's ID for an exchange");
      }
      try {
        return UUID.fromString(replacementShipmentId);
      } catch (IllegalArgumentException e) {
        throw new InvalidRequestException("replacementShipmentId", "must be a UUID");
      }
    }
  }

  /** Why the returns Saga holds an RMA: {@code {"reason"}}. */
  record HoldBody(String reason) {}
}
