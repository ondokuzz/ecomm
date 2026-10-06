package com.ecomm.searchdiscovery.domain;

/**
 * A Product the text query found, before any filter: whether any of its Variants is in Stock (one
 * with no Stock yet isn't), and how well it matched the text, 0 when there was none.
 */
public record Candidate(SearchableProduct product, boolean inStock, double relevance) {}
