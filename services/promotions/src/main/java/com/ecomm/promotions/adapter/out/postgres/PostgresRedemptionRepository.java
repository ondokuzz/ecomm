package com.ecomm.promotions.adapter.out.postgres;

import com.ecomm.promotions.application.port.out.RedemptionRepository;
import com.ecomm.promotions.domain.CouponRedemption;
import com.ecomm.promotions.domain.CouponUsage;
import com.ecomm.promotions.domain.RedemptionHold;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Redemption holds and Coupon redemptions as rows of {@code redemption_hold} and {@code
 * coupon_redemption} (see {@code db/migration}). Deleting a Coupon deletes its holds, but never its
 * redemptions.
 */
@Component
class PostgresRedemptionRepository implements RedemptionRepository {

  private static final String USAGE =
      """
      SELECT
        (SELECT count(*) FROM coupon_redemption
         WHERE coupon_code = :code AND session_id IS DISTINCT FROM CAST(:except AS TEXT) %1$s
        ) AS redemptions,
        (SELECT count(*) FROM redemption_hold
         WHERE coupon_code = :code AND session_id IS DISTINCT FROM CAST(:except AS TEXT)
           AND expires_at > :now %1$s
        ) AS holds
      """;

  private static final RowMapper<CouponUsage> USED =
      (rs, row) -> new CouponUsage(rs.getInt("redemptions"), rs.getInt("holds"));

  private final JdbcClient jdbc;

  PostgresRedemptionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public CouponUsage usage(String couponCode, Instant now, String exceptSession) {
    return usage(USAGE.formatted(""), couponCode, null, now, exceptSession);
  }

  @Override
  public CouponUsage usageBy(
      String couponCode, String customerId, Instant now, String exceptSession) {
    return usage(
        USAGE.formatted("AND customer_id = :customer"), couponCode, customerId, now, exceptSession);
  }

  private CouponUsage usage(
      String sql, String couponCode, String customerId, Instant now, String exceptSession) {
    return jdbc.sql(sql)
        .param("code", couponCode)
        .param("customer", customerId)
        .param("except", exceptSession)
        .param("now", Timestamp.from(now))
        .query(USED)
        .single();
  }

  @Override
  public Map<String, CouponUsage> usageOfEvery(Instant now) {
    var usage = new HashMap<String, CouponUsage>();
    jdbc.sql(
            """
            SELECT coupon_code, sum(redemptions) AS redemptions, sum(holds) AS holds FROM (
              SELECT coupon_code, 1 AS redemptions, 0 AS holds FROM coupon_redemption
              UNION ALL
              SELECT coupon_code, 0, 1 FROM redemption_hold WHERE expires_at > :now
            ) used GROUP BY coupon_code
            """)
        .param("now", Timestamp.from(now))
        .query(
            rs -> {
              usage.put(rs.getString("coupon_code"), USED.mapRow(rs, 0));
            });
    return usage;
  }

  @Override
  public void hold(RedemptionHold hold) {
    jdbc.sql(
            """
            INSERT INTO redemption_hold (session_id, coupon_code, customer_id, expires_at)
            VALUES (:session, :code, :customer, :expiresAt)
            ON CONFLICT (session_id) DO UPDATE SET
              coupon_code = EXCLUDED.coupon_code, customer_id = EXCLUDED.customer_id,
              expires_at = EXCLUDED.expires_at
            """)
        .param("session", hold.sessionId())
        .param("code", hold.couponCode())
        .param("customer", hold.customerId())
        .param("expiresAt", Timestamp.from(hold.expiresAt()))
        .update();
  }

  @Override
  public void release(String sessionId) {
    jdbc.sql("DELETE FROM redemption_hold WHERE session_id = :session")
        .param("session", sessionId)
        .update();
  }

  @Override
  public void redeem(CouponRedemption redemption, Instant at) {
    jdbc.sql(
            """
                INSERT INTO coupon_redemption (session_id, coupon_code, customer_id, redeemed_at)
                VALUES (:session, :code, :customer, :at)
                ON CONFLICT (session_id) DO NOTHING
                """)
        .param("session", redemption.sessionId())
        .param("code", redemption.couponCode())
        .param("customer", redemption.customerId())
        .param("at", Timestamp.from(at))
        .update();
    release(redemption.sessionId());
  }
}
