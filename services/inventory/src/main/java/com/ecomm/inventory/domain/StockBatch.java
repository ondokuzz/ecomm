package com.ecomm.inventory.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Units of several Variants taken together: decremented at once, or held by one Reservation. It
 * applies whole or not at all. A Variant listed more than once counts for the total.
 */
public final class StockBatch {

  public record Line(String variantId, int quantity) {}

  // Sorted, so stock rows are always locked in the same order.
  private final Map<String, Integer> quantityByVariant;

  private StockBatch(Map<String, Integer> quantityByVariant) {
    this.quantityByVariant = quantityByVariant;
  }

  /**
   * Throws {@link InvalidStockRequestException} unless every line names a Variant and a positive
   * quantity.
   */
  public static StockBatch of(List<Line> lines) {
    if (lines == null || lines.isEmpty()) {
      throw new InvalidStockRequestException("a batch needs at least one item");
    }
    var quantities = new TreeMap<String, Integer>();
    for (var line : lines) {
      if (line == null || line.variantId() == null || line.variantId().isBlank()) {
        throw new InvalidStockRequestException("every item needs a variantId");
      }
      if (line.quantity() <= 0) {
        throw new InvalidStockRequestException(
            "the quantity for " + line.variantId() + " must be positive");
      }
      quantities.merge(line.variantId(), line.quantity(), StockBatch::total);
    }
    return new StockBatch(quantities);
  }

  private static int total(int a, int b) {
    try {
      return Math.addExact(a, b);
    } catch (ArithmeticException e) {
      throw new InvalidStockRequestException("a Variant's total quantity is too large");
    }
  }

  /** In a stable order. */
  public Set<String> variantIds() {
    return quantityByVariant.keySet();
  }

  /** Zero for a Variant not in the batch. */
  public int quantityOf(String variantId) {
    return quantityByVariant.getOrDefault(variantId, 0);
  }

  /** One line per Variant, in {@link #variantIds} order. */
  public List<Line> lines() {
    return quantityByVariant.entrySet().stream()
        .map(e -> new Line(e.getKey(), e.getValue()))
        .toList();
  }

  /**
   * Throws {@link UnknownVariantException} for Variants missing from {@code stock}, the current
   * Stock by Variant ID, and {@link InsufficientStockException} when any Variant has less available
   * than the batch needs.
   */
  public void requireAvailableIn(Map<String, Stock> stock) {
    var unknown = variantIds().stream().filter(id -> !stock.containsKey(id)).toList();
    if (!unknown.isEmpty()) {
      throw new UnknownVariantException(unknown);
    }
    var insufficient =
        variantIds().stream().filter(id -> !stock.get(id).covers(quantityOf(id))).toList();
    if (!insufficient.isEmpty()) {
      throw new InsufficientStockException(insufficient);
    }
  }

  /**
   * The on-hand counts left once this batch is taken off {@code onHand}, by Variant ID, as
   * committing a Reservation of it does. The units are already held for it, so there is nothing to
   * check.
   */
  public Map<String, Integer> takenFrom(Map<String, Integer> onHand) {
    var left = new TreeMap<String, Integer>();
    onHand.forEach((id, units) -> left.put(id, units - quantityOf(id)));
    return left;
  }

  /**
   * Takes this batch off {@code stock}'s on-hand counts, after {@link #requireAvailableIn} checks
   * it may.
   */
  public List<Stock> decrementFrom(Map<String, Stock> stock) {
    requireAvailableIn(stock);
    return variantIds().stream().map(id -> stock.get(id).decrementBy(quantityOf(id))).toList();
  }
}
