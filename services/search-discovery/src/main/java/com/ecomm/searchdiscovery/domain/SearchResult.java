package com.ecomm.searchdiscovery.domain;

import java.util.List;

/** One page of the Products a search matched, how many it matched in all, and its facets. */
public record SearchResult(List<Candidate> items, long total, Facets facets) {}
