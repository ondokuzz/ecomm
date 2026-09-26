package com.ecomm.inventory.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A batch of units to take off several Variants' stock at once. It applies whole or not at all. A
 * Variant listed more than once is decremented by the total.
 */
public final class StockDecrement {

  public record Line(String variantId, int quantity) {}

  // Sorted, so stock rows are always locked in the same order.
  private final Map<String, Integer> quantityByVariant;

  private StockDecrement(Map<String, Integer> quantityByVariant) {
    this.quantityByVariant = quantityByVariant;
  }

  /**
   * Throws {@link InvalidStockDecrementException} unless every line names a Variant and a positive
   * quantity.
   */
  public static StockDecrement of(List<Line> lines) {
    if (lines == null || lines.isEmpty()) {
      throw new InvalidStockDecrementException("a decrement needs at least one item");
    }
    var quantities = new TreeMap<String, Integer>();
    for (var line : lines) {
      if (line == null || line.variantId() == null || line.variantId().isBlank()) {
        throw new InvalidStockDecrementException("every item needs a variantId");
      }
      if (line.quantity() <= 0) {
        throw new InvalidStockDecrementException(
            "the quantity for " + line.variantId() + " must be positive");
      }
      quantities.merge(line.variantId(), line.quantity(), StockDecrement::total);
    }
    return new StockDecrement(quantities);
  }

  private static int total(int a, int b) {
    try {
      return Math.addExact(a, b);
    } catch (ArithmeticException e) {
      throw new InvalidStockDecrementException("a Variant's total quantity is too large");
    }
  }

  /** In a stable order. */
  public Set<String> variantIds() {
    return quantityByVariant.keySet();
  }

  /**
   * Takes this batch off {@code stock}, the current stock of the Variants in it. Throws {@link
   * UnknownVariantException} for Variants missing from {@code stock} and {@link
   * InsufficientStockException} when any Variant would go negative.
   */
  public List<Stock> applyTo(List<Stock> stock) {
    var current = stock.stream().collect(Collectors.toMap(Stock::variantId, Function.identity()));
    var unknown = variantIds().stream().filter(id -> !current.containsKey(id)).toList();
    if (!unknown.isEmpty()) {
      throw new UnknownVariantException(unknown);
    }
    var insufficient =
        variantIds().stream()
            .filter(id -> !current.get(id).covers(quantityByVariant.get(id)))
            .toList();
    if (!insufficient.isEmpty()) {
      throw new InsufficientStockException(insufficient);
    }
    return variantIds().stream()
        .map(id -> current.get(id).decrementBy(quantityByVariant.get(id)))
        .toList();
  }
}
