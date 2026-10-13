package com.ecomm.fulfillmentshipping.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every configured Warehouse, in configuration order. Exactly one is the default, IDs are unique,
 * and no two share a country, so that every destination has exactly one Warehouse to ship from.
 */
public final class Warehouses {

  private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

  private final List<Warehouse> all;
  private final Warehouse defaultWarehouse;

  /**
   * @throws IllegalArgumentException naming what's wrong, when the Warehouses break a rule above or
   *     one has a blank ID or name, or a country that isn't an ISO 3166-1 alpha-2 code
   */
  public Warehouses(List<Warehouse> warehouses) {
    if (warehouses == null || warehouses.isEmpty()) {
      throw new IllegalArgumentException("At least one Warehouse must be configured");
    }
    var ids = new HashSet<String>();
    var countries = new HashSet<String>();
    for (var warehouse : warehouses) {
      if (blank(warehouse.id()) || blank(warehouse.name())) {
        throw new IllegalArgumentException("Every Warehouse needs an ID and a name");
      }
      if (warehouse.address() == null || !COUNTRIES.contains(warehouse.country())) {
        throw new IllegalArgumentException(
            "Warehouse " + warehouse.id() + " needs an ISO 3166-1 alpha-2 country");
      }
      if (!ids.add(warehouse.id())) {
        throw new IllegalArgumentException("Warehouse " + warehouse.id() + " is configured twice");
      }
      if (!countries.add(warehouse.country())) {
        throw new IllegalArgumentException(
            "Two Warehouses are in " + warehouse.country() + "; a country has at most one");
      }
    }
    var defaults = warehouses.stream().filter(Warehouse::isDefault).toList();
    if (defaults.size() != 1) {
      throw new IllegalArgumentException(
          "Exactly one Warehouse must be the default, but " + defaults.size() + " are");
    }
    this.all = List.copyOf(warehouses);
    this.defaultWarehouse = defaults.getFirst();
  }

  public List<Warehouse> all() {
    return all;
  }

  /** The Warehouse in {@code country}, else the default: where a Shipment there ships from. */
  public Warehouse assignedTo(String country) {
    return all.stream()
        .filter(w -> w.country().equals(country))
        .findFirst()
        .orElse(defaultWarehouse);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
