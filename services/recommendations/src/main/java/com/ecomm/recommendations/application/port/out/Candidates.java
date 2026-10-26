package com.ecomm.recommendations.application.port.out;

import com.ecomm.recommendations.domain.Candidate;
import com.ecomm.recommendations.domain.Category;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Works out, from the projection, which Products may be recommended. Only counting Orders placed at
 * or after {@code since} count, and a Product removed from the Catalog, or none of whose current
 * Variants is in Stock, is never one.
 */
public interface Candidates {

  /** The other Products bought in the Orders that bought any of {@code sku}'s Variants. */
  List<Candidate> boughtWith(String sku, Instant since);

  /** The Product's Category, if Recommendations knows it and it hasn't been removed. */
  Optional<Category> categoryOf(String sku);

  /** The Category's Products bought in at least one Order. */
  List<Candidate> bestSellersIn(String categorySlug, Instant since);
}
