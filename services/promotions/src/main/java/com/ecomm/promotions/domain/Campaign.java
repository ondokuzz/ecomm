package com.ecomm.promotions.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A Discount Staff run for every qualifying Checkout Session, with no code. It applies while it is
 * {@code active}, from {@code validFrom} up to but not including {@code validUntil}, to the lines
 * in its {@code categories} (every line when there are none), on a subtotal of at least its
 * optional {@code minimumSubtotal}. Campaigns apply in the order of their {@code priority}, lowest
 * first, and no two have the same. Throws {@link InvalidPromotionException}, naming the field,
 * unless every field is valid on its own; whether the Categories and currencies are Catalog's, and
 * the priority free, is for the use case to check.
 */
public record Campaign(
    UUID id,
    String name,
    DiscountRule discount,
    List<String> categories,
    Money minimumSubtotal,
    Instant validFrom,
    Instant validUntil,
    boolean active,
    int priority) {

  /** The longest name a Campaign can have. */
  public static final int MAX_NAME_LENGTH = 100;

  public Campaign {
    if (id == null) {
      throw new IllegalArgumentException("a Campaign needs an ID");
    }
    if (name == null || name.isBlank()) {
      throw new InvalidPromotionException("name", "is needed");
    }
    name = name.strip();
    if (name.length() > MAX_NAME_LENGTH) {
      throw new InvalidPromotionException(
          "name", "must be at most " + MAX_NAME_LENGTH + " characters");
    }
    Terms.check(discount, minimumSubtotal, validFrom, validUntil);
    categories = categories == null ? List.of() : List.copyOf(categories);
    var seen = new HashSet<String>();
    for (var i = 0; i < categories.size(); i++) {
      if (categories.get(i).isBlank()) {
        throw new InvalidPromotionException("categories[" + i + "]", "must be a Category's slug");
      }
      if (!seen.add(categories.get(i))) {
        throw new InvalidPromotionException("categories[" + i + "]", "is listed twice");
      }
    }
    if (priority < 0) {
      throw new InvalidPromotionException("priority", "must be 0 or more");
    }
  }

  /** Where this Campaign stands at {@code now}. */
  public CampaignState stateAt(Instant now) {
    if (!active) {
      return CampaignState.OFF;
    }
    if (now.isBefore(validFrom)) {
      return CampaignState.SCHEDULED;
    }
    return now.isBefore(validUntil) ? CampaignState.RUNNING : CampaignState.OVER;
  }

  /**
   * The ISO 4217 code of each currency it names, by the field naming it: {@code
   * discount.amountOff.currency} and {@code minimumSubtotal.currency}, when it has them.
   */
  public Map<String, String> currencies() {
    var currencies = new LinkedHashMap<String, String>();
    if (discount instanceof DiscountRule.AmountOff a) {
      currencies.put("discount.amountOff.currency", a.amount().currency().getCurrencyCode());
    }
    if (minimumSubtotal != null) {
      currencies.put("minimumSubtotal.currency", minimumSubtotal.currency().getCurrencyCode());
    }
    return currencies;
  }
}
