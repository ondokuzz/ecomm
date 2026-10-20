package com.ecomm.inventory.application;

import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.inventory.application.port.in.Page;
import com.ecomm.inventory.application.port.in.PublishBackfillUseCase;
import com.ecomm.inventory.application.port.in.ReadStockMovementsUseCase;
import com.ecomm.inventory.application.port.in.ReadStockUseCase;
import com.ecomm.inventory.application.port.in.ReleaseExpiredReservationsUseCase;
import com.ecomm.inventory.application.port.in.RemoveStockUseCase;
import com.ecomm.inventory.application.port.in.ReserveStockUseCase;
import com.ecomm.inventory.application.port.in.RestockUseCase;
import com.ecomm.inventory.application.port.in.SetOnHandUseCase;
import com.ecomm.inventory.application.port.in.SettleReservationUseCase;
import com.ecomm.inventory.application.port.out.CachedStock;
import com.ecomm.inventory.application.port.out.ReservationRepository;
import com.ecomm.inventory.application.port.out.StockCache;
import com.ecomm.inventory.application.port.out.StockEvent;
import com.ecomm.inventory.application.port.out.StockEvent.Change;
import com.ecomm.inventory.application.port.out.StockMovementRepository;
import com.ecomm.inventory.application.port.out.StockRepository;
import com.ecomm.inventory.application.port.out.TimeSource;
import com.ecomm.inventory.application.port.out.Transactions;
import com.ecomm.inventory.domain.InsufficientStockException;
import com.ecomm.inventory.domain.Reservation;
import com.ecomm.inventory.domain.Stock;
import com.ecomm.inventory.domain.StockBatch;
import com.ecomm.inventory.domain.StockMovement;
import com.ecomm.inventory.domain.UnknownVariantException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * every Variant it touched, under a new version. Once it commits, it writes that Stock to the Stock
 * cache, which serves Stock reads and refuses a Reservation it already shows can't be met.
 */
public class InventoryService
    implements ReadStockUseCase,
        ReadStockMovementsUseCase,
        SetOnHandUseCase,
        RemoveStockUseCase,
        RestockUseCase,
        ReserveStockUseCase,
        SettleReservationUseCase,
        ReleaseExpiredReservationsUseCase,
        PublishBackfillUseCase {

  /** Why Staff's removal of a Variant took its On-hand to 0. */
  static final String REMOVAL_REASON = "stopped stocking";

  /** How many Variants the backfill publishes per transaction. */
  private static final int BACKFILL_BATCH = 100;

  /** The longest a Stock cache entry is trusted, unless a Reservation holding it expires sooner. */
  static final Duration CACHE_FRESHNESS = Duration.ofSeconds(60);

  private final StockRepository stock;
  private final ReservationRepository reservations;
  private final StockMovementRepository movements;
  private final Transactions transactions;
  private final IntegrationEventPublisher events;
  private final TimeSource time;
  private final StockCache cache;

  public InventoryService(
      StockRepository stock,
      ReservationRepository reservations,
      StockMovementRepository movements,
      Transactions transactions,
      IntegrationEventPublisher events,
      TimeSource time,
      StockCache cache) {
    this.stock = stock;
    this.reservations = reservations;
    this.movements = movements;
    this.transactions = transactions;
    this.events = events;
    this.time = time;
    this.cache = cache;
  }

  @Override
  public Optional<Stock> stock(String variantId) {
    return stocks(List.of(variantId)).stream().findFirst();
  }

  @Override
  public List<Stock> stocks(Collection<String> variantIds) {
    var asked = new LinkedHashSet<>(variantIds);
    var cached = cache.get(asked, time.now());
    var missed = asked.stream().filter(id -> !cached.containsKey(id)).toList();
    var read = missed.isEmpty() ? Map.<String, CachedStock>of() : readStock(missed);
    cache.put(read.values(), time.now());
    return asked.stream()
        .map(id -> cached.getOrDefault(id, read.get(id)))
        .filter(Objects::nonNull)
        .flatMap(entry -> entry.stock().stream())
        .toList();
  }

  /**
   * The Stock of those Variants that exist, from Postgres, as Stock cache entries. One snapshot for
   * both reads, so a change landing between them can't mismatch the counts.
   */
  private Map<String, CachedStock> readStock(Collection<String> variantIds) {
    return transactions.inSnapshot(
        () -> {
          var onHand = stock.onHandWithVersions(variantIds);
          var holding = reservations.activeFor(onHand.keySet());
          var now = time.now();
          return onHand.entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      e ->
                          cacheEntry(
                              Stock.of(e.getKey(), e.getValue().units(), holding, now),
                              e.getValue().version(),
                              holding,
                              now)));
        });
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
            cacheOnCommit(List.of(cacheEntry(created, inserted.getAsLong(), List.of(), now)));
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
          var now = time.now();
          StockMovement.adjusted(variantId, current.onHand(), 0, REMOVAL_REASON, now)
              .ifPresent(m -> movements.append(List.of(m)));
          events.publish(StockEvent.removed(variantId, version));
          cacheOnCommit(
              List.of(CachedStock.removed(variantId, version, now.plus(CACHE_FRESHNESS))));
          return true;
        });
  }

  @Override
  public Stock restock(String variantId, int quantity, String reference) {
    var now = time.now();
    var movement = StockMovement.restocked(variantId, quantity, reference, now);
    return transactions.inTransaction(
        () -> {
          var current = lockStock(List.of(variantId)).get(variantId);
          if (current == null) {
            throw new UnknownVariantException(List.of(variantId));
          }
          var changed = current.withOnHand(Math.addExact(current.onHand(), quantity));
          recordChange(
              Change.RESTOCKED, List.of(movement), Map.of(variantId, changed.onHand()), now);
          return changed;
        });
  }

  /**
   * An RMA's Reservation is looked for first, before the Stock cache can refuse a retry whose first
   * attempt took the last unit, and again once the stock rows are locked: two first attempts for
   * the same RMA lock the same rows, so the second finds what the first made. One that expired
   * uncommitted is released then, as the sweeper would, so the RMA can hold Stock again.
   */
  @Override
  public Reserved reserve(String owner, StockBatch items, Instant expiresAt) {
    var forRma = Reservation.isRmaOwner(owner);
    var previous = forRma ? reservations.findForRma(owner) : Optional.<Reservation>empty();
    if (previous.isPresent() && previous.get().answersAgainAt(time.now())) {
      return new Reserved(previous.get(), false);
    }
    var reservation = Reservation.create(UUID.randomUUID(), owner, items, expiresAt, time.now());
    refuseWhatTheCacheShowsShort(items);
    return transactions.inTransaction(
        () -> {
          var toLock = new HashSet<>(items.variantIds());
          previous.ifPresent(r -> toLock.addAll(r.items().variantIds()));
          var locked = lockStock(toLock);
          var current = forRma ? reservations.findForRma(owner) : Optional.<Reservation>empty();
          if (current.isPresent()) {
            if (current.get().answersAgainAt(time.now())) {
              return new Reserved(current.get(), false);
            }
            releaseExpiredOfRma(current.get(), locked);
          }
          items.requireAvailableIn(locked);
          reservations.insert(reservation);
          var now = time.now();
          var onHand = onHandOf(locked.values());
          onHand.keySet().retainAll(items.variantIds());
          recordChange(Change.RESERVED, StockMovement.reserved(reservation, now), onHand, now);
          return new Reserved(reservation, true);
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

  /**
   * Releases an RMA's Reservation that expired uncommitted, recording its {@code RELEASED}
   * movements as the sweeper would. Call it inside a transaction holding the stock rows in {@code
   * locked}; it locks any of the Reservation's it is missing.
   */
  private void releaseExpiredOfRma(Reservation expired, Map<String, Stock> locked) {
    var variantIds = expired.items().variantIds();
    var stocks = new HashMap<>(locked);
    stocks.putAll(lockStock(variantIds.stream().filter(id -> !locked.containsKey(id)).toList()));
    var now = time.now();
    if (reservations.releaseExpired(List.of(expired.id()), now).isEmpty()) {
      return;
    }
    var onHand = onHandOf(stocks.values());
    onHand.keySet().retainAll(variantIds);
    recordChange(Change.RELEASED, StockMovement.released(expired.release(), now), onHand, now);
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
   * Throws {@link InsufficientStockException}, without touching Postgres, when the Stock cache
   * already shows too little available of some Variant in the batch. Otherwise Postgres decides.
   */
  private void refuseWhatTheCacheShowsShort(StockBatch items) {
    var cached = cache.get(items.variantIds(), time.now());
    var wanting =
        items.variantIds().stream()
            .filter(id -> cached.containsKey(id) && cached.get(id).stocked())
            .filter(id -> cached.get(id).available() < items.quantityOf(id))
            .toList();
    if (!wanting.isEmpty()) {
      throw new InsufficientStockException(wanting);
    }
  }

  /**
   * The Stock cache entry of {@code stock} at {@code version}: fresh for {@link #CACHE_FRESHNESS},
   * or until the first of the Reservations holding some of it expires, giving its units back.
   */
  private static CachedStock cacheEntry(
      Stock stock, long version, Collection<Reservation> holding, Instant now) {
    var maxFreshness = now.plus(CACHE_FRESHNESS);
    var freshUntil =
        Stock.nextExpiry(stock.variantId(), holding, now)
            .filter(expiry -> expiry.isBefore(maxFreshness))
            .orElse(maxFreshness);
    return CachedStock.of(stock, version, freshUntil);
  }

  /** Writes the entries to the Stock cache once the surrounding transaction commits. */
  private void cacheOnCommit(List<CachedStock> entries) {
    transactions.afterCommit(() -> cache.put(entries, time.now()));
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
    var entries = new ArrayList<CachedStock>();
    versions.forEach(
        (variantId, version) -> {
          var changed = Stock.of(variantId, onHandByVariant.get(variantId), holding, now);
          events.publish(StockEvent.of(changed, version, change));
          entries.add(cacheEntry(changed, version, holding, now));
        });
    cacheOnCommit(entries);
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
