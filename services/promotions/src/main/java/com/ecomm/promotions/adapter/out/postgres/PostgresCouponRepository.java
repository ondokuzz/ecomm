package com.ecomm.promotions.adapter.out.postgres;

import com.ecomm.commons.money.Money;
import com.ecomm.promotions.application.port.out.CouponRepository;
import com.ecomm.promotions.domain.Coupon;
import com.ecomm.promotions.domain.CouponDiscount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Coupons as rows of the {@code coupon} table (see {@code db/migration}). */
@Component
class PostgresCouponRepository implements CouponRepository {

  private static final String PERCENT_OFF = "PERCENT_OFF";
  private static final String AMOUNT_OFF = "AMOUNT_OFF";

  private static final String COLUMNS =
      """
      code, discount_type, percent_off, amount_off_minor, amount_off_currency,
      minimum_subtotal_minor, minimum_subtotal_currency, valid_from, valid_until, active
      """;

  private static final RowMapper<Coupon> COUPON =
      (rs, row) ->
          new Coupon(
              rs.getString("code"),
              discount(rs),
              money(rs, "minimum_subtotal_minor", "minimum_subtotal_currency"),
              rs.getTimestamp("valid_from").toInstant(),
              rs.getTimestamp("valid_until").toInstant(),
              rs.getBoolean("active"));

  private final JdbcClient jdbc;

  PostgresCouponRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean add(Coupon coupon) {
    try {
      jdbc.sql(
              "INSERT INTO coupon ("
                  + COLUMNS
                  + """
                  ) VALUES (
                    :code, :discountType, :percentOff, :amountOffMinor, :amountOffCurrency,
                    :minimumSubtotalMinor, :minimumSubtotalCurrency, :validFrom, :validUntil,
                    :active)
                  """)
          .params(params(coupon))
          .update();
      return true;
    } catch (DuplicateKeyException e) {
      return false;
    }
  }

  @Override
  public boolean replace(Coupon coupon) {
    return jdbc.sql(
                """
                UPDATE coupon SET
                  discount_type = :discountType, percent_off = :percentOff,
                  amount_off_minor = :amountOffMinor, amount_off_currency = :amountOffCurrency,
                  minimum_subtotal_minor = :minimumSubtotalMinor,
                  minimum_subtotal_currency = :minimumSubtotalCurrency,
                  valid_from = :validFrom, valid_until = :validUntil, active = :active
                WHERE code = :code
                """)
            .params(params(coupon))
            .update()
        == 1;
  }

  @Override
  public boolean remove(String code) {
    return jdbc.sql("DELETE FROM coupon WHERE code = :code").param("code", code).update() == 1;
  }

  @Override
  public Optional<Coupon> find(String code) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM coupon WHERE code = :code")
        .param("code", code)
        .query(COUPON)
        .optional();
  }

  @Override
  public List<Coupon> all() {
    return jdbc.sql("SELECT " + COLUMNS + " FROM coupon ORDER BY code").query(COUPON).list();
  }

  private static Map<String, Object> params(Coupon coupon) {
    var params = new HashMap<String, Object>();
    params.put("code", coupon.code());
    switch (coupon.discount()) {
      case CouponDiscount.PercentOff p -> {
        params.put("discountType", PERCENT_OFF);
        params.put("percentOff", p.percent());
        params.put("amountOffMinor", null);
        params.put("amountOffCurrency", null);
      }
      case CouponDiscount.AmountOff a -> {
        params.put("discountType", AMOUNT_OFF);
        params.put("percentOff", null);
        params.put("amountOffMinor", a.amount().amountMinor());
        params.put("amountOffCurrency", a.amount().currency().getCurrencyCode());
      }
    }
    var minimum = coupon.minimumSubtotal();
    params.put("minimumSubtotalMinor", minimum == null ? null : minimum.amountMinor());
    params.put(
        "minimumSubtotalCurrency", minimum == null ? null : minimum.currency().getCurrencyCode());
    params.put("validFrom", Timestamp.from(coupon.validFrom()));
    params.put("validUntil", Timestamp.from(coupon.validUntil()));
    params.put("active", coupon.active());
    return params;
  }

  private static CouponDiscount discount(ResultSet rs) throws SQLException {
    return switch (rs.getString("discount_type")) {
      case PERCENT_OFF -> new CouponDiscount.PercentOff(rs.getInt("percent_off"));
      case AMOUNT_OFF ->
          new CouponDiscount.AmountOff(money(rs, "amount_off_minor", "amount_off_currency"));
      default ->
          throw new IllegalStateException("unknown discount_type " + rs.getString("discount_type"));
    };
  }

  private static Money money(ResultSet rs, String amountColumn, String currencyColumn)
      throws SQLException {
    var currency = rs.getString(currencyColumn);
    return currency == null ? null : Money.of(rs.getLong(amountColumn), currency);
  }
}
