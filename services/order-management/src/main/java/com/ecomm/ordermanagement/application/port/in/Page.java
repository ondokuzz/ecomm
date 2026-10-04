package com.ecomm.ordermanagement.application.port.in;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a list: its {@code items}, which page it is (from 0), how many a page holds, and how
 * many there are in all.
 */
public record Page<T>(List<T> items, int page, int size, long total) {

  public Page {
    items = List.copyOf(items);
  }

  public <R> Page<R> map(Function<T, R> mapper) {
    return new Page<>(items.stream().map(mapper).toList(), page, size, total);
  }
}
