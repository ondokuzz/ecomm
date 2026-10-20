package com.ecomm.inventory.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * An all-or-nothing hold on a batch of Variants for its owner: a Customer, by their ID, or an RMA
 * whose replacement it holds, as {@code rma:<rmaId>}. An RMA has at most one. While {@code ACTIVE}
 * and before {@code expiresAt} it holds its Stock; committing it takes that Stock off on-hand for
 * good, and releasing it gives the Stock back. An {@code ACTIVE} Reservation past its expiry holds
 * nothing, whether or not the sweeper has released it yet.
 */
public record Reservation(
    UUID id, String customerId, StockBatch items, ReservationStatus status, Instant expiresAt) {

  /** How the owner of an RMA's Reservation starts. */
  public static final String RMA_OWNER_PREFIX = "rma:";

  /**
   * A new {@code ACTIVE} Reservation. Throws {@link InvalidStockRequestException} unless it names
   * its Customer and expires after {@code now}.
   */
  public static Reservation create(
      UUID id, String customerId, StockBatch items, Instant expiresAt, Instant now) {
    requireValidCustomerId(customerId);
    if (expiresAt == null) {
      throw new InvalidStockRequestException("a Reservation needs an expiresAt");
    }
    if (!expiresAt.isAfter(now)) {
      throw new InvalidStockRequestException("a Reservation must expire in the future");
    }
    return new Reservation(id, customerId, items, ReservationStatus.ACTIVE, expiresAt);
  }

  /** Throws {@link InvalidStockRequestException} unless it can name a Reservation's owner. */
  public static void requireValidCustomerId(String customerId) {
    if (customerId == null || customerId.isBlank()) {
      throw new InvalidStockRequestException("a Reservation needs a customerId");
    }
  }

  public boolean isOwnedBy(String customerId) {
    return this.customerId.equals(customerId);
  }

  /** Whether {@code owner} names an RMA, as {@code rma:<rmaId>}, rather than a Customer. */
  public static boolean isRmaOwner(String owner) {
    return owner != null
        && owner.startsWith(RMA_OWNER_PREFIX)
        && !owner.substring(RMA_OWNER_PREFIX.length()).isBlank();
  }

  /** Its owner, {@code rma:<rmaId>}, when it holds an RMA's replacement; empty for a Customer's. */
  public Optional<String> rmaOwner() {
    return isRmaOwner(customerId) ? Optional.of(customerId) : Optional.empty();
  }

  public boolean holdsStockAt(Instant now) {
    return status == ReservationStatus.ACTIVE && now.isBefore(expiresAt);
  }

  /**
   * Whether asking its RMA's Reservation again answers this one: it is committed, or still holds
   * its Stock. One that expired uncommitted can never be committed, so the RMA holds Stock anew.
   */
  public boolean answersAgainAt(Instant now) {
    return status == ReservationStatus.COMMITTED || holdsStockAt(now);
  }

  /**
   * This Reservation {@code COMMITTED}; committing it again changes nothing. Throws {@link
   * ReservationExpiredException} once it has expired, even if the sweeper has released it since,
   * and {@link ReservationReleasedException} if it was released before then.
   */
  public Reservation commitAt(Instant now) {
    if (status == ReservationStatus.COMMITTED) {
      return this;
    }
    if (!now.isBefore(expiresAt)) {
      throw new ReservationExpiredException(id);
    }
    if (status == ReservationStatus.RELEASED) {
      throw new ReservationReleasedException(id);
    }
    return withStatus(ReservationStatus.COMMITTED);
  }

  /**
   * This Reservation {@code RELEASED}; releasing it again changes nothing. Throws {@link
   * ReservationCommittedException} once it has been committed.
   */
  public Reservation release() {
    if (status == ReservationStatus.COMMITTED) {
      throw new ReservationCommittedException(id);
    }
    return withStatus(ReservationStatus.RELEASED);
  }

  private Reservation withStatus(ReservationStatus status) {
    return new Reservation(id, customerId, items, status, expiresAt);
  }
}
