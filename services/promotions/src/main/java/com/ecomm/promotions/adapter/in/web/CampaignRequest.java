package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.domain.Campaign;
import com.ecomm.promotions.domain.InvalidPromotionException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Campaign as Staff send it: {@code {"name", "discount", "categories", "minimumSubtotal",
 * "validFrom", "validUntil", "active", "priority"}}, with {@code categories} and {@code
 * minimumSubtotal} optional. Its ID comes from the path, or is new. The values are taken raw so
 * that nothing is coerced.
 */
record CampaignRequest(
    Object name,
    DiscountRuleBody discount,
    Object categories,
    Amount minimumSubtotal,
    Object validFrom,
    Object validUntil,
    Object active,
    Object priority) {

  Campaign toCampaign(UUID id) {
    var named = Fields.string("name", name);
    var rule = DiscountRuleBody.toRule(discount);
    var slugs = slugs();
    var minimum = minimumSubtotal == null ? null : minimumSubtotal.toMoney("minimumSubtotal");
    var from = Fields.instant("validFrom", validFrom);
    var until = Fields.instant("validUntil", validUntil);
    var isActive = Fields.bool("active", active);
    if (!(priority instanceof Integer rank)) {
      throw new InvalidPromotionException("priority", "must be a whole number, 0 or more");
    }
    return new Campaign(id, named, rule, slugs, minimum, from, until, isActive, rank);
  }

  private List<String> slugs() {
    if (categories == null) {
      return List.of();
    }
    if (!(categories instanceof List<?> list)) {
      throw new InvalidPromotionException("categories", "must be a list of Category slugs");
    }
    var slugs = new ArrayList<String>();
    for (var i = 0; i < list.size(); i++) {
      if (!(list.get(i) instanceof String slug)) {
        throw new InvalidPromotionException("categories[" + i + "]", "must be a Category's slug");
      }
      slugs.add(slug);
    }
    return slugs;
  }
}
