package com.ecomm.inventory.application;

import com.ecomm.inventory.application.port.in.ReadStockUseCase;
import com.ecomm.inventory.application.port.in.ReleaseExpiredReservationsUseCase;
import com.ecomm.inventory.application.port.in.RemoveStockUseCase;
import com.ecomm.inventory.application.port.in.ReserveStockUseCase;
import com.ecomm.inventory.application.port.in.SetOnHandUseCase;
import com.ecomm.inventory.application.port.in.SettleReservationUseCase;
import com.ecomm.inventory.application.port.out.ReservationRepository;
import com.ecomm.inventory.application.port.out.StockRepository;
import com.ecomm.inventory.application.port.out.TimeSource;
import com.ecomm.inventory.application.port.out.Transactions;
import com.ecomm.inventory.domain.Reservation;
import com.ecomm.inventory.domain.Stock;
import com.ecomm.inventory.domain.StockBatch;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every change to Stock locks the stock rows of the Variants it touches, in Variant ID order,
 * before it reads what Reservations hold of them; a change to a Reservation then locks that
 * Reservation. Taking locks in that one order keeps concurrent changes from deadlocking, and means
 * two Reservations of the last unit can't both succeed.
 */
public class InventoryService
    implements ReadStockUseCase,
        SetOnHandUseCase,
        RemoveStockUseCase,
        ReserveStockUseCase,
        SettleReservationUseCase,
        ReleaseExpiredReservationsUseCase {

  private final StockRepository stock;
  private final ReservationRepository reservations;
  private final Transactions transactions;
  private final TimeSource time;

  public InventoryService(
      StockRepository stock,
      ReservationRepository reservations,
      Transactions transactions,
      TimeSource time) {
    this.stock = stock;
    this.reservations = reservations;
    this.transactions = transactions;
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
  public Result setOnHand(String variantId, int onHand) {
    var created = Stock.newVariant(variantId, onHand);
    return transactions.inTransaction(
        () -> {
          if (stock.insertIfAbsent(variantId, onHand)) {
            return new Result(created, true);
          }
          var changed = lockStock(List.of(variantId)).get(variantId).withOnHand(onHand);
          stock.setOnHand(Map.of(variantId, onHand));
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
          stock.delete(variantId);
          return true;
        });
  }

  @Override
  public Reservation reserve(String customerId, StockBatch items, Instant expiresAt) {
    var reservation =
        Reservation.create(UUID.randomUUID(), customerId, items, expiresAt, time.now());
    return transactions.inTransaction(
        () -> {
          items.requireAvailableIn(lockStock(items.variantIds()));
          reservations.insert(reservation);
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
                      var committed = current.commitAt(time.now());
                      if (committed.status() != current.status()) {
                        stock.setOnHand(current.items().takenFrom(onHand));
                        reservations.updateStatus(id, committed.status());
                      }
                      return committed;
                    }));
  }

  @Override
  public Optional<Reservation> release(String customerId, UUID id) {
    // Releasing only ever frees Stock, so it needs no stock row locks.
    return transactions.inTransaction(
        () ->
            owned(customerId, id)
                .map(
                    found -> {
                      var current = reservations.lock(id).orElseThrow();
                      var released = current.release();
                      if (released.status() != current.status()) {
                        reservations.updateStatus(id, released.status());
                      }
                      return released;
                    }));
  }

  @Override
  public List<UUID> releaseExpired() {
    return transactions.inTransaction(() -> reservations.releaseExpired(time.now()));
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
    var onHand = stock.lockOnHand(variantIds);
    var holding = reservations.activeFor(onHand.keySet());
    var now = time.now();
    return onHand.entrySet().stream()
        .map(e -> Stock.of(e.getKey(), e.getValue(), holding, now))
        .collect(Collectors.toMap(Stock::variantId, Function.identity()));
  }
}
