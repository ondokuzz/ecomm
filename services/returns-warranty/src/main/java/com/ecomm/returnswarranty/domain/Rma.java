package com.ecomm.returnswarranty.domain;

import com.ecomm.returnswarranty.domain.RmaExceptions.NoWarrantyException;
import com.ecomm.returnswarranty.domain.RmaExceptions.OutsideWindowException;
import com.ecomm.returnswarranty.domain.RmaExceptions.QuantityExceededException;
import com.ecomm.returnswarranty.domain.RmaExceptions.RmaAlreadyDecidedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumberAlreadyClaimedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumberNotShippedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumbersMiscountedException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * An RMA: a Customer's request to return units of one Variant from a delivered Order, or to claim
 * their warranty, and Staff's decision on it. It keeps the window it was checked against and its
 * decision history, which only grows.
 *
 * @param version goes up by one with every change
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
    List<RmaDecision> decisions) {

  public Rma {
    serialNumbers = List.copyOf(serialNumbers);
    decisions = List.copyOf(decisions);
  }

  /**
   * Requests an RMA against the Order's delivered Shipment, checking it against the Customer's
   * other RMAs and the window for its kind.
   *
   * @param others the Customer's other RMAs; those for another Order or Variant are ignored
   * @param warranty the Variant's Product's Warranty terms, for a warranty claim
   * @throws QuantityExceededException when fewer units are free to claim
   * @throws SerialNumbersMiscountedException unless there is one Serial number per unit of a
   *     serial-tracked Variant, and none for an untracked one
   * @throws SerialNumberNotShippedException for one that didn't ship on the Order for the Variant
   * @throws SerialNumberAlreadyClaimedException for one another open or approved RMA holds
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
      Instant now) {
    var held =
        others.stream()
            .filter(
                other ->
                    other.holdsUnits()
                        && other.orderId().equals(delivery.orderId())
                        && other.variantId().equals(request.variantId()))
            .toList();
    var line = delivery.lineOf(request.variantId());
    var available =
        line.map(DeliveredLine::quantity).orElse(0) - held.stream().mapToInt(Rma::quantity).sum();
    if (line.isEmpty() || request.quantity() > available) {
      throw new QuantityExceededException(request.variantId(), Math.max(available, 0));
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
        List.of());
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
      var serialNumber = entered == null ? "" : entered.strip().toUpperCase(Locale.ROOT);
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

  /** Whether it holds its units against other RMAs: until it is rejected. */
  public boolean holdsUnits() {
    return status != RmaStatus.REJECTED;
  }

  public boolean belongsTo(String customerId) {
    return this.customerId.equals(customerId);
  }

  /** The short handle it is shown by, like an Order reference: {@code RMA-3F2A9C1B}. */
  public String reference() {
    return "RMA-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }

  /** Staff approve it, with an optional note. */
  public Rma approve(String staffId, String note, Instant at) {
    return decide(new RmaDecision(RmaStatus.APPROVED, staffId, at, note));
  }

  /** Staff reject it, with the reason. */
  public Rma reject(String staffId, String reason, Instant at) {
    return decide(new RmaDecision(RmaStatus.REJECTED, staffId, at, reason));
  }

  private Rma decide(RmaDecision decision) {
    if (status != RmaStatus.REQUESTED) {
      throw new RmaAlreadyDecidedException(id, status);
    }
    var history = new ArrayList<>(decisions);
    history.add(decision);
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
        decision.status(),
        requestedAt,
        history);
  }
}
