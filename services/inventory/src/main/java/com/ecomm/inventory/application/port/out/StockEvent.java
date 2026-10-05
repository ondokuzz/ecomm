package com.ecomm.inventory.application.port.out;

import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.inventory.domain.Stock;

/**
 * The {@code inventory.stock} event: a snapshot of a Variant's Stock, published whenever its
 * On-hand, its available Stock or whether it is stocked changes, and once for every Variant stocked
 * before Stock events. Its shape is defined by {@code
 * platform/event-schemas/schemas/inventory.stock.json}.
 */
public record StockEvent(String variantId, long version, Change change, Snapshot stock)
    implements IntegrationEvent {

  public static final String TOPIC = "inventory.stock";

  /** Why the event was published: the kind of movement, or the Variant's removal or backfill. */
  public enum Change {
    ADJUSTED,
    RESERVED,
    RELEASED,
    COMMITTED,
    RECEIVED,
    RESTOCKED,
    REMOVED,
    BACKFILLED
  }

  /** {@code available} is On-hand less what Reservations hold. */
  public record Snapshot(int onHand, int available, boolean stocked) {}

  public static StockEvent of(Stock stock, long version, Change change) {
    return new StockEvent(
        stock.variantId(), version, change, new Snapshot(stock.onHand(), stock.available(), true));
  }

  /** The last event of a Variant Inventory stops stocking: nothing on hand, nothing available. */
  public static StockEvent removed(String variantId, long version) {
    return new StockEvent(variantId, version, Change.REMOVED, new Snapshot(0, 0, false));
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public String aggregateId() {
    return variantId;
  }
}
