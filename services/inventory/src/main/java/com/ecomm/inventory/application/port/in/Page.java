package com.ecomm.inventory.application.port.in;

import java.util.List;

/**
 * One page of a list: its {@code items}, which page it is (from 0), how many a page holds, and how
 * many there are in all.
 */
public record Page<T>(List<T> items, int page, int size, long total) {

  public Page {
    items = List.copyOf(items);
  }
}
