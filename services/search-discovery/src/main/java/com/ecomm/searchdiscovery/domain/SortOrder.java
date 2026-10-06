package com.ecomm.searchdiscovery.domain;

/** How a search orders its results. */
public enum SortOrder {
  /** Best text match first; without a text query, the same as {@link #NEWEST}. */
  RELEVANCE,
  /** Most recently listed first. */
  NEWEST,
  /** Lowest "from" Price first. */
  PRICE_ASC,
  /** Highest "from" Price first. */
  PRICE_DESC
}
