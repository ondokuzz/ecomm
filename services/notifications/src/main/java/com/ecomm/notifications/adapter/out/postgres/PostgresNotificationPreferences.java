package com.ecomm.notifications.adapter.out.postgres;

import com.ecomm.notifications.application.port.out.NotificationPreferencesStore;
import com.ecomm.notifications.domain.NotificationPreferences;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Notification preferences as rows of {@code notification_preferences}, one per Customer. */
@Component
class PostgresNotificationPreferences implements NotificationPreferencesStore {

  private final JdbcClient jdbc;

  PostgresNotificationPreferences(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public NotificationPreferences preferencesOf(String customerId) {
    return jdbc.sql("SELECT * FROM notification_preferences WHERE customer_id = :customerId")
        .param("customerId", customerId)
        .query(
            (rs, n) ->
                new NotificationPreferences(
                    rs.getString("customer_id"),
                    rs.getString("phone_number"),
                    rs.getBoolean("sms"),
                    rs.getBoolean("push"),
                    rs.getTimestamp("updated_at").toInstant()))
        .optional()
        .orElseGet(() -> NotificationPreferences.defaults(customerId));
  }

  @Override
  public void save(NotificationPreferences p) {
    jdbc.sql(
            """
            INSERT INTO notification_preferences (customer_id, phone_number, sms, push, updated_at)
            VALUES (:customerId, :phoneNumber, :sms, :push, :updatedAt)
            ON CONFLICT (customer_id) DO UPDATE SET phone_number = excluded.phone_number,
              sms = excluded.sms, push = excluded.push, updated_at = excluded.updated_at
            """)
        .param("customerId", p.customerId())
        .param("phoneNumber", p.phoneNumber())
        .param("sms", p.sms())
        .param("push", p.push())
        .param("updatedAt", Timestamp.from(p.updatedAt()))
        .update();
  }
}
