package com.ecomm.ordermanagement.adapter.in.web;

/** Checks the {@code page} and {@code size} a list was asked for. */
final class PageRequests {

  /** The most Orders a page of a list can hold. */
  static final int MAX_PAGE_SIZE = 100;

  private PageRequests() {}

  /**
   * Throws {@link InvalidPageException} unless {@code page} counts from 0 and {@code size} is 1 to
   * {@value #MAX_PAGE_SIZE}.
   */
  static void check(int page, int size) {
    if (page < 0) {
      throw new InvalidPageException("page counts from 0");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidPageException("size is 1 to " + MAX_PAGE_SIZE);
    }
  }
}
