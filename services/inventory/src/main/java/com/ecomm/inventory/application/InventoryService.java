package com.ecomm.inventory.application;

import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.inventory.application.port.in.Page;
import com.ecomm.inventory.application.port.in.PublishBackfillUseCase;
import com.ecomm.inventory.application.port.in.ReadStockMovementsUseCase;
import com.ecomm.inventory.application.port.in.ReadStockUseCase;
import com.ecomm.inventory.application.port.in.ReleaseExpiredReservationsUseCase;
import com.ecomm.inventory.application.port.in.RemoveStockUseCase;
import com.ecomm.inventory.application.port.in.ReserveStockUseCase;
import com.ecomm.inventory.application.port.in.SetOnHandUseCase;
import com.ecomm.inventory.application.port.in.SettleReservationUseCase;
import com.ecomm.inventory.application.port.out.ReservationRepository;
import com.ecomm.inventory.application.port.out.StockEvent;
import com.ecomm.inventory.application.port.out.StockEvent.Change;
import com.ecomm.inventory.application.port.out.StockMovementRepository;
import com.ecomm.inventory.application.port.out.StockRepository;
import com.ecomm.inventory.application.port.out.TimeSource;
import com.ecomm.inventory.application.port.out.Transactions;
import com.ecomm.inventory.domain.Reservation;
import com.ecomm.inventory.domain.Stock;
import com.ecomm.inventory.domain.StockBatch;
import com.ecomm.inventory.domain.StockMovement;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every change to Stock locks the stock rows of the Variants it touches, in Variant ID order,
 * before it reads what Reservations hold of them; a change to a Reservation then locks that
 * Reservation. Taking locks in that one order keeps concurrent changes from deadlocking, and means
 * two Reservations of the last unit can't both succeed.
 *
 * <p>In the same transaction, each change records its Stock movements and publishes the Stock of
 * every Variant it touched, under a new version.
 */
public class InventoryService
    implements ReadStockUseCase,
        ReadStockMovementsUseCase,
        SetOnHandUseCase,
        RemoveStockUseCase,
        ReserveStockUseCase,
        SettleReservationUseCase,
        ReleaseExpiredReservationsUseCase,
        PublishBackfillUseCase {

  /** Why Staff's removal of a Variant took its On-hand to 0. */
  static final String REMOVAL_REASON = "stopped stocking";

  /** How many Variants the backfill publishes per transaction. */
  private static final int BACKFILL_BATCH = 100;

  private final StockRepository stock;
  private final ReservationRepository reservations;
  private final StockMovementRepository movements;
  private final Transactions transactions;
  private final IntegrationEventPublisher events;
  private final TimeSource time;

  public InventoryService(
      StockRepository stock,
      ReservationRepository reservations,
      StockMovementRepository movements,
      Transactions transactions,
      IntegrationEventPublisher events,
      TimeSource time) {
    this.stock = stock;
    this.reservations = reservations;
    this.movements = movements;
    this.transactions = transactions;
    this.events = events;
    this.time = time;
  }

  @Override
  public Optional<Stock> stock(String variantId) {
    // One snapshot for both reads, so a change landing between them can't mismatch the counts.
    return transactions.inSnapshot(
        () ->
            stock
                .onHand(variantId)
                .map(
                    onHand ->
                        Stock.of(
                            variantId,
                            onHand,
                            reservations.activeFor(List.of(variantId)),
                            time.now())));
  }

  @Override
  public Optional<StockLedger> movements(String variantId, int page, int size) {
    // One snapshot, so On-hand and the sum of the movements describe the same moment.
    return transactions.inSnapshot(
        () ->
            stock
                .onHand(variantId)
                .map(
                    onHand ->
                        new StockLedger(
                            variantId,
                            onHand,
                            movements.onHandSum(variantId),
                            new Page<>(
                                movements.newestFirst(variantId, (long) page * size, size),
                                page,
                                size,
                                movements.count(variantId)))));
  }

  @Override
  public Result setOnHand(String variantId, int onHand, String reason) {
    var created = Stock.newVariant(variantId, onHand);
    var validReason = StockMovement.validReason(reason);
    return transactions.inTransaction(
        () -> {
          var now = time.now();
          var inserted = stock.insertIfAbsent(variantId, onHand);
          if (inserted.isPresent()) {
            StockMovement.adjusted(variantId, 0, onHand, validReason, now)
                .ifPresent(m -> movements.append(List.of(m)));
            events.publish(StockEvent.of(created, inserted.getAsLong(), Change.ADJUSTED));
            return new Result(created, true);
          }
          var current = lockStock(List.of(variantId)).get(variantId);
          var changed = current.withOnHand(onHand);
          StockMovement.adjusted(variantId, current.onHand(), onHand, validReason, now)
              .ifPresent(
                  movement ->
                      recordChange(
                          Change.ADJUSTED, List.of(movement), Map.of(variantId, onHand), now));
          return new Result(changed, false);
        });
  }

  @Override
  public boolean removeStock(String variantId) {
    return transactions.inTransaction(
        () -> {
          var current = lockStock(List.of(variantId)).get(variantId);
          if (current == null) {
            return false;
          }
          current.requireUnreserved();
          var version = stock.delete(variantId);
          StockMovement.adjusted(variantId, current.onHand(), 0, REMOVAL_REASON, time.now())
              .ifPresent(m -> movements.append(List.of(m)));
          events.publish(StockEvent.removed(variantId, version));
          return true;
        });
  }

  @Override
  public Reservation reserve(String customerId, StockBatch items, Instant expiresAt) {
    var reservation =
        Reservation.create(UUID.randomUUID(), customerId, items, expiresAt, time.now());
    return transactions.inTransaction(
        () -> {
          var locked = lockStock(items.variantIds());
          items.requireAvailableIn(locked);
          reservations.insert(reservation);
          var now = time.now();
          recordChange(
              Change.RESERVED,
              StockMovement.reserved(reservation, now),
              onHandOf(locked.values()),
              now);
          return reservation;
        });
  }

  @Override
  public Optional<Reservation> commit(String customerId, UUID id) {
    return transactions.inTransaction(
        () ->
            owned(customerId, id)
                .map(
                    found -> {
                      // Lock the stock rows before the Reservation, as every other change does.
                      var onHand = stock.lockOnHand(found.items().variantIds());
                      var current = reservations.lock(id).orElseThrow();
                      var now = time.now();
                      var committed = current.commitAt(now);
                      if (committed.status() != current.status()) {
                        reservations.updateStatus(id, committed.status());
                        recordChange(
                            Change.COMMITTED,
                            StockMovement.committed(committed, now),
                            current.items().takenFrom(onHand),
                            now);
                      }
                      return committed;
                    }));
  }

  @Override
  public Optional<Reservation> release(String customerId, UUID id) {
    return transactions.inTransaction(
        () ->
            owned(customerId, id)
                .map(
                    found -> {
                      // Lock the stock rows before the Reservation, as every other change does.
                      var onHand = stock.lockOnHand(found.items().variantIds());
                      var current = reservations.lock(id).orElseThrow();
                      var released = current.release();
                      if (released.status() != current.status()) {
                        reservations.updateStatus(id, released.status());
                        var now = time.now();
                        recordChange(
                            Change.RELEASED, StockMovement.released(released, now), onHand, now);
                      }
                      return released;
                    }));
  }

  @Override
  public List<UUID> releaseExpired() {
    return transactions.inTransaction(
        () -> {
          var now = time.now();
          var expired = reservations.expired(now);
          if (expired.isEmpty()) {
            return List.of();
          }
          // Lock the stock rows before the Reservations, as every other change does.
          var onHand = stock.lockOnHand(variantIdsOf(expired));
          var released =
              new HashSet<>(
                  reservations.releaseExpired(expired.stream().map(Reservation::id).toList(), now));
          var releasedReservations =
              expired.stream().filter(r -> released.contains(r.id())).toList();
          var touched = variantIdsOf(releasedReservations);
          onHand.keySet().retainAll(touched);
          recordChange(
              Change.RELEASED,
              releasedReservations.stream()
                  .flatMap(r -> StockMovement.released(r, now).stream())
                  .toList(),
              onHand,
              now);
          return releasedReservations.stream().map(Reservation::id).toList();
        });
  }

  @Override
  public int publishBackfill() {
    var published = 0;
    while (true) {
      var batch =
          transactions.inTransaction(
              () -> {
                var awaiting = stock.lockAwaitingBackfillEvent(BACKFILL_BATCH);
                var locked = lockStock(awaiting);
                // A new version, so the backfill never loses to an event published before it.
                var versions = stock.update(onHandOf(locked.values()));
                versions.forEach(
                    (variantId, version) ->
                        events.publish(
                            StockEvent.of(locked.get(variantId), version, Change.BACKFILLED)));
                stock.markBackfillPublished(awaiting);
                return awaiting.size();
              });
      if (batch == 0) {
        return published;
      }
      published += batch;
    }
  }

  /**
   * Records the movements, writes the on-hand counts of the Variants they touched, and publishes
   * each of those Variants' Stock as it now is. Call it inside a transaction holding their stock
   * rows. A Variant missing from {@code onHandByVariant}, being no longer stocked, gets its
   * movements but no event.
   */
  private void recordChange(
      Change change, List<StockMovement> made, Map<String, Integer> onHandByVariant, Instant now) {
    movements.append(made);
    if (onHandByVariant.isEmpty()) {
      return;
    }
    var versions = stock.update(onHandByVariant);
    var holding = reservations.activeFor(versions.keySet());
    versions.forEach(
        (variantId, version) ->
            events.publish(
                StockEvent.of(
                    Stock.of(variantId, onHandByVariant.get(variantId), holding, now),
                    version,
                    change)));
  }

  private Optional<Reservation> owned(String customerId, UUID id) {
    Reservation.requireValidCustomerId(customerId);
    return reservations.find(id).filter(r -> r.isOwnedBy(customerId));
  }

  /**
   * The current Stock of those Variants that exist, by Variant ID, with their rows locked. Call it
   * inside a transaction.
   */
  private Map<String, Stock> lockStock(Collection<String> variantIds) {
    if (variantIds.isEmpty()) {
      return Map.of();
    }
    var onHand = stock.lockOnHand(variantIds);
    var holding = reservations.activeFor(onHand.keySet());
    var now = time.now();
    return onHand.entrySet().stream()
        .map(e -> Stock.of(e.getKey(), e.getValue(), holding, now))
        .collect(Collectors.toMap(Stock::variantId, Function.identity()));
  }

  private static Set<String> variantIdsOf(Collection<Reservation> held) {
    var variantIds = new HashSet<String>();
    held.forEach(r -> variantIds.addAll(r.items().variantIds()));
    return variantIds;
  }

  private static Map<String, Integer> onHandOf(Collection<Stock> stocks) {
    return stocks.stream().collect(Collectors.toMap(Stock::variantId, Stock::onHand));
  }
}
