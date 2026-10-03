package com.ecomm.promotions.adapter.out.postgres;

import com.ecomm.commons.money.Money;
import com.ecomm.promotions.domain.DiscountRule;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * The columns the {@code coupon} and {@code campaign} tables share for a discount and a minimum
 * subtotal: {@code discount_type} is {@code PERCENT_OFF}, with {@code percent_off}, or {@code
 * AMOUNT_OFF}, with {@code amount_off_minor} and {@code amount_off_currency}.
 */
final class TermColumns {

  private static final String PERCENT_OFF = "PERCENT_OFF";
  private static final String AMOUNT_OFF = "AMOUNT_OFF";

  private TermColumns() {}

  /** Puts {@code discount} and {@code minimum} into {@code params}, under the statements' names. */
  static void put(Map<String, Object> params, DiscountRule discount, Money minimum) {
    switch (discount) {
      case DiscountRule.PercentOff p -> {
        params.put("discountType", PERCENT_OFF);
        params.put("percentOff", p.percent());
        params.put("amountOffMinor", null);
        params.put("amountOffCurrency", null);
      }
      case DiscountRule.AmountOff a -> {
        params.put("discountType", AMOUNT_OFF);
        params.put("percentOff", null);
        params.put("amountOffMinor", a.amount().amountMinor());
        params.put("amountOffCurrency", a.amount().currency().getCurrencyCode());
      }
    }
    params.put("minimumSubtotalMinor", minimum == null ? null : minimum.amountMinor());
    params.put(
        "minimumSubtotalCurrency", minimum == null ? null : minimum.currency().getCurrencyCode());
  }

  static DiscountRule discount(ResultSet rs) throws SQLException {
    return switch (rs.getString("discount_type")) {
      case PERCENT_OFF -> new DiscountRule.PercentOff(rs.getInt("percent_off"));
      case AMOUNT_OFF ->
          new DiscountRule.AmountOff(money(rs, "amount_off_minor", "amount_off_currency"));
      default ->
          throw new IllegalStateException("unknown discount_type " + rs.getString("discount_type"));
    };
  }

  static Money minimumSubtotal(ResultSet rs) throws SQLException {
    return money(rs, "minimum_subtotal_minor", "minimum_subtotal_currency");
  }

  private static Money money(ResultSet rs, String amountColumn, String currencyColumn)
      throws SQLException {
    var currency = rs.getString(currencyColumn);
    return currency == null ? null : Money.of(rs.getLong(amountColumn), currency);
  }
}
