package com.ecomm.returnswarranty.domain;

import com.ecomm.returnswarranty.domain.RmaExceptions.NoWarrantyException;
import com.ecomm.returnswarranty.domain.RmaExceptions.OutcomeNotForKindException;
import com.ecomm.returnswarranty.domain.RmaExceptions.OutsideWindowException;
import com.ecomm.returnswarranty.domain.RmaExceptions.QuantityExceededException;
import com.ecomm.returnswarranty.domain.RmaExceptions.QuantityMismatchException;
import com.ecomm.returnswarranty.domain.RmaExceptions.ResolutionContradictsInspectionException;
import com.ecomm.returnswarranty.domain.RmaExceptions.RmaAlreadyDecidedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialMismatchException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumberAlreadyClaimedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumberNotShippedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumbersMiscountedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.TransitionRefusedException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An RMA: a Customer's request to return units of one Variant from a delivered Order, or to claim
 * their warranty, and what became of it. It keeps the window it was checked against, its Refund
 * amount, and once approved its Send-back deadline and the Warehouse to send back to; then its
 * Inspection and its resolution. Its RMA history, which only grows, has an entry for every step.
 *
 * @param version goes up by one with every change
 * @param refundAmount what refunding its units gives back; null for an RMA requested before its
 *     Order was projected, until it is
 * @param sendBackBy its Send-back deadline; null until it is approved
 * @param warehouseId the Warehouse its units go back to; null until it is approved, and until a
 *     Shipment projected before Warehouses were kept is projected again
 * @param inspection null until it is inspected
 * @param resolution null until it is refunded, exchanged or closed
 */
public record Rma(
    UUID id,
    long version,
    String customerId,
    String orderId,
    String variantId,
    String productName,
    int quantity,
    List<String> serialNumbers,
    RmaKind kind,
    String reason,
    Window window,
    RmaStatus status,
    Instant requestedAt,
    Money refundAmount,
    Instant sendBackBy,
    String warehouseId,
    Inspection inspection,
    Resolution resolution,
    List<RmaHistoryEntry> history) {

  public Rma {
    serialNumbers = List.copyOf(serialNumbers);
    history = List.copyOf(history);
  }

  /**
   * Requests an RMA against the Order's delivered Shipment, checking it against the Customer's
   * other RMAs and the window for its kind, and works out its Refund amount from the Order.
   *
   * @param others the Customer's other RMAs; those for another Order or Variant are ignored
   * @param warranty the Variant's Product's Warranty terms, for a warranty claim
   * @throws QuantityExceededException when fewer units are free to claim
   * @throws SerialNumbersMiscountedException unless there is one Serial number per unit of a
   *     serial-tracked Variant, and none for an untracked one
   * @throws SerialNumberNotShippedException for one that didn't ship on the Order for the Variant
   * @throws SerialNumberAlreadyClaimedException for one another RMA holds
   * @throws NoWarrantyException for a warranty claim on a Product without Warranty terms
   * @throws OutsideWindowException after the Return Period or Warranty Window ended
   */
  public static Rma request(
      UUID id,
      RmaRequest request,
      Delivery delivery,
      Collection<Rma> others,
      Duration returnPeriod,
      Optional<WarrantyTerms> warranty,
      Order order,
      Instant now) {
    var held = holding(others, delivery.orderId(), request.variantId());
    var line = delivery.lineOf(request.variantId());
    var available = line.map(l -> Availability.of(l, held).quantity()).orElse(0);
    if (line.isEmpty() || request.quantity() > available) {
      throw new QuantityExceededException(request.variantId(), available);
    }
    var serialNumbers = claimedSerialNumbers(request, line.get(), held);
    var window =
        switch (request.kind()) {
          case RETURN -> Window.returnPeriod(delivery.deliveredAt(), returnPeriod);
          case WARRANTY_CLAIM ->
              Window.warrantyWindow(
                  delivery.deliveredAt(),
                  warranty.orElseThrow(() -> new NoWarrantyException(request.variantId())));
        };
    if (!window.isOpenAt(now)) {
      throw new OutsideWindowException(window);
    }
    return new Rma(
        id,
        1,
        request.customerId(),
        delivery.orderId(),
        request.variantId(),
        line.get().productName(),
        request.quantity(),
        serialNumbers,
        request.kind(),
        request.reason(),
        window,
        RmaStatus.REQUESTED,
        now,
        refundAmountOf(order, request.variantId(), request.quantity(), held),
        null,
        null,
        null,
        null,
        List.of(new RmaHistoryEntry(RmaStatus.REQUESTED, RmaHistoryEntry.CUSTOMER, now, null)));
  }

  /** The RMAs of {@code others} that hold units of the Order's Variant. */
  static List<Rma> holding(Collection<Rma> others, String orderId, String variantId) {
    return others.stream()
        .filter(
            other ->
                other.holdsUnits()
                    && other.orderId().equals(orderId)
                    && other.variantId().equals(variantId))
        .toList();
  }

  /**
   * The Refund amount of {@code quantity} units of the Variant, given the RMAs holding its other
   * units; those without a Refund amount yet count for nothing.
   */
  private static Money refundAmountOf(
      Order order, String variantId, int quantity, Collection<Rma> held) {
    return order.refundAmount(
        variantId,
        quantity,
        held.stream()
            .filter(rma -> rma.refundAmount() != null)
            .map(rma -> new Order.Held(rma.quantity(), rma.refundAmount()))
            .toList());
  }

  private static List<String> claimedSerialNumbers(
      RmaRequest request, DeliveredLine line, List<Rma> held) {
    var expected = line.serialTracking().tracked() ? request.quantity() : 0;
    if (request.serialNumbers().size() != expected) {
      throw new SerialNumbersMiscountedException(expected);
    }
    var claimed = new HashSet<String>();
    held.forEach(other -> claimed.addAll(other.serialNumbers()));
    var serialNumbers = new ArrayList<String>();
    for (var entered : request.serialNumbers()) {
      var serialNumber = normalized(entered);
      if (!line.serialNumbers().contains(serialNumber)) {
        throw new SerialNumberNotShippedException(serialNumber);
      }
      if (!claimed.add(serialNumber)) {
        throw new SerialNumberAlreadyClaimedException(serialNumber);
      }
      serialNumbers.add(serialNumber);
    }
    return serialNumbers;
  }

  /** A Serial number as entered, matched whatever its case and surrounding spaces. */
  private static String normalized(String entered) {
    return entered == null ? "" : entered.strip().toUpperCase(Locale.ROOT);
  }

  /** Whether it holds its units against other RMAs: until it is rejected, expires or is closed. */
  public boolean holdsUnits() {
    return status.holdsUnits();
  }

  public boolean belongsTo(String customerId) {
    return this.customerId.equals(customerId);
  }

  /** The short handle it is shown by, like an Order reference: {@code RMA-3F2A9C1B}. */
  public String reference() {
    return "RMA-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }

  /**
   * Staff approve it, with an optional note. Its Send-back deadline is the later of its window's
   * end and {@code sendBackPeriod} after the approval.
   *
   * @param warehouseId the Warehouse its Shipment came from; null when that isn't known yet
   */
  public Rma approve(
      String staffId, String note, Instant at, Duration sendBackPeriod, String warehouseId) {
    decidable();
    var fromApproval = at.plus(sendBackPeriod);
    var deadline = window.endsAt().isAfter(fromApproval) ? window.endsAt() : fromApproval;
    return moved(
        new RmaHistoryEntry(RmaStatus.APPROVED, staffId, at, note),
        deadline,
        warehouseId,
        inspection,
        resolution);
  }

  /** Staff reject it, with the reason. */
  public Rma reject(String staffId, String reason, Instant at) {
    decidable();
    return moved(new RmaHistoryEntry(RmaStatus.REJECTED, staffId, at, reason));
  }

  private void decidable() {
    if (status != RmaStatus.REQUESTED) {
      throw new RmaAlreadyDecidedException(id, status);
    }
  }

  /**
   * Staff receive its units: by Serial number, each the RMA's and every one of them, or for a
   * Variant without Serial tracking, by confirming their quantity.
   *
   * @param serialNumbers as entered, matched whatever their case; empty for untracked units
   * @param quantity how many untracked units arrived; ignored when Serial numbers are given
   * @throws TransitionRefusedException unless it is {@code APPROVED}
   * @throws SerialMismatchException naming each Serial number that isn't the RMA's
   * @throws QuantityMismatchException when the units don't number the RMA's
   */
  public Rma receive(String staffId, List<String> serialNumbers, int quantity, Instant at) {
    allowed(RmaStatus.RECEIVED);
    var entered = serialNumbers.stream().map(Rma::normalized).toList();
    var wrong = entered.stream().filter(s -> !this.serialNumbers.contains(s)).toList();
    if (!wrong.isEmpty()) {
      throw new SerialMismatchException(wrong);
    }
    var tracked = !this.serialNumbers.isEmpty();
    var matches =
        tracked
            ? entered.size() == this.serialNumbers.size()
                && Set.copyOf(entered).equals(Set.copyOf(this.serialNumbers))
            : quantity == this.quantity;
    if (!matches) {
      throw new QuantityMismatchException(this.quantity);
    }
    return moved(new RmaHistoryEntry(RmaStatus.RECEIVED, staffId, at, null));
  }

  /**
   * Staff record its Inspection.
   *
   * @param note required for a refusing outcome, which the web layer checks
   * @throws TransitionRefusedException unless it is {@code RECEIVED}
   * @throws OutcomeNotForKindException for an outcome of the other kind of RMA
   */
  public Rma inspect(String staffId, InspectionOutcome outcome, String note, Instant at) {
    allowed(RmaStatus.INSPECTED);
    if (outcome.kind() != kind) {
      throw new OutcomeNotForKindException(outcome, kind);
    }
    return moved(
        new RmaHistoryEntry(RmaStatus.INSPECTED, staffId, at, note),
        sendBackBy,
        warehouseId,
        new Inspection(outcome, note),
        resolution);
  }

  /**
   * The returns Saga resolves it: {@code REFUNDED} with the Refund's Payment transaction reference,
   * after an accepted return or a confirmed fault with no replacement; {@code EXCHANGED} with the
   * replacement Shipment, after a confirmed fault; or {@code CLOSED}, after a refusing outcome.
   *
   * @throws TransitionRefusedException unless it is {@code INSPECTED}
   * @throws ResolutionContradictsInspectionException for a resolution its Inspection doesn't allow
   */
  public Rma resolve(
      RmaStatus resolution,
      String paymentTransactionReference,
      UUID replacementShipmentId,
      String note,
      Instant at) {
    allowed(resolution);
    var outcome = inspection.outcome();
    var follows =
        switch (resolution) {
          case REFUNDED -> !outcome.refusing();
          case EXCHANGED -> outcome == InspectionOutcome.FAULT_CONFIRMED;
          case CLOSED -> outcome.refusing();
          default -> throw new IllegalArgumentException(resolution + " isn't a resolution");
        };
    if (!follows) {
      throw new ResolutionContradictsInspectionException(resolution, inspection);
    }
    return moved(
        new RmaHistoryEntry(resolution, RmaHistoryEntry.ORCHESTRATION, at, note),
        sendBackBy,
        warehouseId,
        inspection,
        new Resolution(
            resolution == RmaStatus.REFUNDED
                ? Objects.requireNonNull(paymentTransactionReference)
                : null,
            resolution == RmaStatus.EXCHANGED
                ? Objects.requireNonNull(replacementShipmentId)
                : null));
  }

  /**
   * The returns Saga holds it, with the reason, such as a declined Refund's.
   *
   * @throws TransitionRefusedException unless it is {@code INSPECTED}
   */
  public Rma hold(String reason, Instant at) {
    allowed(RmaStatus.ON_HOLD);
    return moved(new RmaHistoryEntry(RmaStatus.ON_HOLD, RmaHistoryEntry.ORCHESTRATION, at, reason));
  }

  /**
   * The returns Saga expires it once its Send-back deadline passes without its units.
   *
   * @throws TransitionRefusedException unless it is {@code APPROVED}, so a receipt that raced the
   *     timer wins
   */
  public Rma expire(Instant at) {
    allowed(RmaStatus.EXPIRED);
    return moved(new RmaHistoryEntry(RmaStatus.EXPIRED, RmaHistoryEntry.ORCHESTRATION, at, null));
  }

  private void allowed(RmaStatus next) {
    if (!status.canMoveTo(next)) {
      throw new TransitionRefusedException(id, status, next);
    }
  }

  /**
   * Whether it is approved, or later, without the Warehouse it is sent back to: an RMA approved
   * against a Shipment projected before Warehouses were.
   */
  public boolean lacksWarehouse() {
    return sendBackBy != null && warehouseId == null;
  }

  /**
   * It with the Warehouse it is sent back to, once that is known. It is a change, so its version
   * goes up, but not a step: its history stays.
   */
  public Rma withWarehouse(String warehouseId) {
    return new Rma(
        id,
        version + 1,
        customerId,
        orderId,
        variantId,
        productName,
        quantity,
        serialNumbers,
        kind,
        reason,
        window,
        status,
        requestedAt,
        refundAmount,
        sendBackBy,
        warehouseId,
        inspection,
        resolution,
        history);
  }

  /**
   * It with its Refund amount, once its Order is known, given the Order's other RMAs. It is a
   * change, so its version goes up, but not a step: its history stays.
   */
  public Rma withRefundAmount(Order order, Collection<Rma> others) {
    var held =
        holding(others, orderId, variantId).stream().filter(rma -> !rma.id().equals(id)).toList();
    var amount = refundAmountOf(order, variantId, quantity, held);
    return new Rma(
        id,
        version + 1,
        customerId,
        orderId,
        variantId,
        productName,
        quantity,
        serialNumbers,
        kind,
        reason,
        window,
        status,
        requestedAt,
        amount,
        sendBackBy,
        warehouseId,
        inspection,
        resolution,
        history);
  }

  private Rma moved(RmaHistoryEntry entry) {
    return moved(entry, sendBackBy, warehouseId, inspection, resolution);
  }

  private Rma moved(
      RmaHistoryEntry entry,
      Instant sendBackBy,
      String warehouseId,
      Inspection inspection,
      Resolution resolution) {
    var appended = new ArrayList<>(history);
    appended.add(entry);
    return new Rma(
        id,
        version + 1,
        customerId,
        orderId,
        variantId,
        productName,
        quantity,
        serialNumbers,
        kind,
        reason,
        window,
        entry.status(),
        requestedAt,
        refundAmount,
        sendBackBy,
        warehouseId,
        inspection,
        resolution,
        appended);
  }
}
