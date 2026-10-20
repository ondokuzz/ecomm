package com.ecomm.inventory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One change to a Variant's On-hand units or to what Reservations hold of them, recorded for good:
 * by how much each count changed, which Reservation made it, if any, and why. A Variant's On-hand
 * is the sum of its {@code onHandChange}s.
 *
 * @param reservationId {@code null} unless a Reservation made it
 * @param reason {@code null} unless one was given
 */
public record StockMovement(
    String variantId,
    Kind kind,
    int onHandChange,
    int reservedChange,
    UUID reservationId,
    String reason,
    String reference,
    Instant at) {

  /** The longest reason Inventory stores. */
  public static final int MAX_REASON_LENGTH = 200;

  /** The longest reference a restock names. */
  public static final int MAX_REFERENCE_LENGTH = 64;

  /** What made the movement. */
  public enum Kind {
    /** Staff set On-hand: the difference. */
    ADJUSTED,
    /** A Reservation started holding units; On-hand is unchanged. */
    RESERVED,
    /** A Reservation stopped holding units, given back. */
    RELEASED,
    /** A Reservation's units left On-hand for good. */
    COMMITTED,
    /** Units arrived from a supplier. Unused until replenishment exists. */
    RECEIVED,
    /** Returned units went back on hand, naming the RMA they came back on. */
    RESTOCKED
  }

  /**
   * Staff moving On-hand from {@code from} to {@code to}; empty when that changes nothing.
   *
   * @param reason {@code null} when none was given
   */
  public static Optional<StockMovement> adjusted(
      String variantId, int from, int to, String reason, Instant at) {
    if (from == to) {
      return Optional.empty();
    }
    return Optional.of(
        new StockMovement(variantId, Kind.ADJUSTED, to - from, 0, null, reason, null, at));
  }

  /**
   * {@code quantity} returned units back on hand, for the reference. Throws {@link
   * InvalidStockRequestException} unless the quantity is positive and the reference is 1 to {@value
   * #MAX_REFERENCE_LENGTH} characters.
   */
  public static StockMovement restocked(
      String variantId, int quantity, String reference, Instant at) {
    if (quantity < 1) {
      throw new InvalidStockRequestException("a restock's quantity is at least 1");
    }
    if (reference == null
        || reference.isBlank()
        || reference.strip().length() > MAX_REFERENCE_LENGTH) {
      throw new InvalidStockRequestException(
          "a restock's reference is 1 to " + MAX_REFERENCE_LENGTH + " characters");
    }
    return new StockMovement(
        variantId, Kind.RESTOCKED, quantity, 0, null, null, reference.strip(), at);
  }

  /** One {@code RESERVED} movement per Variant the Reservation holds. */
  public static List<StockMovement> reserved(Reservation reservation, Instant at) {
    return ofEachLine(reservation, Kind.RESERVED, 0, 1, at);
  }

  /** One {@code RELEASED} movement per Variant the Reservation held. */
  public static List<StockMovement> released(Reservation reservation, Instant at) {
    return ofEachLine(reservation, Kind.RELEASED, 0, -1, at);
  }

  /** One {@code COMMITTED} movement per Variant the Reservation held. */
  public static List<StockMovement> committed(Reservation reservation, Instant at) {
    return ofEachLine(reservation, Kind.COMMITTED, -1, -1, at);
  }

  /**
   * The reason as Inventory keeps it: {@code null} when it is missing or blank. Throws {@link
   * InvalidStockRequestException} when it is longer than {@value #MAX_REASON_LENGTH} characters.
   */
  public static String validReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return null;
    }
    var stripped = reason.strip();
    if (stripped.length() > MAX_REASON_LENGTH) {
      throw new InvalidStockRequestException(
          "a reason is at most " + MAX_REASON_LENGTH + " characters");
    }
    return stripped;
  }

  private static List<StockMovement> ofEachLine(
      Reservation reservation, Kind kind, int onHandSign, int reservedSign, Instant at) {
    return reservation.items().lines().stream()
        .map(
            line ->
                new StockMovement(
                    line.variantId(),
                    kind,
                    onHandSign * line.quantity(),
                    reservedSign * line.quantity(),
                    reservation.id(),
                    null,
                    reservation.rmaOwner().orElse(null),
                    at))
        .toList();
  }
}
