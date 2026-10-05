package com.ecomm.inventory.application.port.out;

import com.ecomm.inventory.domain.Reservation;
import com.ecomm.inventory.domain.ReservationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {

  void insert(Reservation reservation);

  Optional<Reservation> find(UUID id);

  /**
   * Like {@link #find}, but locked until the surrounding transaction ends. Callers lock the stock
   * rows it holds first, so every transaction takes its locks in the same order.
   */
  Optional<Reservation> lock(UUID id);

  void updateStatus(UUID id, ReservationStatus status);

  /**
   * The {@code ACTIVE} Reservations naming any of these Variants, expired or not: whether one still
   * holds Stock is the domain's call.
   */
  List<Reservation> activeFor(Collection<String> variantIds);

  /** The {@code ACTIVE} Reservations that expired by {@code now}, unlocked. */
  List<Reservation> expired(Instant now);

  /**
   * Marks those of {@code ids} still {@code ACTIVE} and expired by {@code now} as {@code RELEASED},
   * and returns their IDs. Skips any another transaction has locked, which is committing or
   * releasing it. Callers lock the stock rows they hold first.
   */
  List<UUID> releaseExpired(Collection<UUID> ids, Instant now);
}
