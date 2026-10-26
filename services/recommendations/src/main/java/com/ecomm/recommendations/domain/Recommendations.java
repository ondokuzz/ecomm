package com.ecomm.recommendations.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** How a Product's Recommendations are ranked, and filled up when too few were bought together. */
public final class Recommendations {

  private static final Comparator<Candidate> BOUGHT_TOGETHER =
      Comparator.comparingLong(Candidate::orders)
          .reversed()
          .thenComparing(Candidate::latestOrder, Comparator.reverseOrder())
          .thenComparing(Candidate::sku);

  private static final Comparator<Candidate> BEST_SELLERS =
      Comparator.comparingLong(Candidate::orders).reversed().thenComparing(Candidate::sku);

  private Recommendations() {}

  /**
   * Up to {@code limit} Recommendations for {@code sku}: those bought together with it, by the
   * number of Orders, then the latest Order, then SKU; then, while there are fewer than {@code
   * limit}, its {@code category}'s best sellers, by the number of Orders, then SKU, leaving out the
   * Product itself and any already listed. With no Category known, nothing is filled.
   */
  public static List<Recommendation> rank(
      String sku,
      Collection<Candidate> boughtTogether,
      Optional<Category> category,
      Collection<Candidate> bestSellers,
      int limit) {
    var ranked = new ArrayList<Recommendation>();
    var listed = new HashSet<String>();
    listed.add(sku);
    boughtTogether.stream()
        .sorted(BOUGHT_TOGETHER)
        .limit(limit)
        .forEach(
            c -> {
              ranked.add(new Recommendation(c.sku(), c.orders(), Reason.BOUGHT_TOGETHER, null));
              listed.add(c.sku());
            });
    category.ifPresent(
        popularIn ->
            bestSellers.stream()
                .sorted(BEST_SELLERS)
                .filter(c -> !listed.contains(c.sku()))
                .limit(limit - ranked.size())
                .forEach(
                    c ->
                        ranked.add(
                            new Recommendation(
                                c.sku(),
                                c.orders(),
                                Reason.POPULAR_IN_CATEGORY,
                                popularIn.name()))));
    return List.copyOf(ranked);
  }
}
