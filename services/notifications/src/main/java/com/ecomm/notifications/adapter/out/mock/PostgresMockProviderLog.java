package com.ecomm.notifications.adapter.out.mock;

import com.ecomm.notifications.application.port.in.MockedMessage;
import com.ecomm.notifications.application.port.in.Page;
import com.ecomm.notifications.application.port.out.MockProviderLog;
import com.ecomm.notifications.domain.NotificationChannel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads what the mock providers recorded in {@code mock_sms_message} and {@code mock_push_message}.
 */
@Component
class PostgresMockProviderLog implements MockProviderLog {

  private final JdbcClient jdbc;

  PostgresMockProviderLog(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Page<MockedMessage> find(
      NotificationChannel channel, String customerId, int page, int size) {
    var table =
        switch (channel) {
          case SMS -> "mock_sms_message";
          case PUSH -> "mock_push_message";
          case EMAIL -> throw new IllegalArgumentException("Email has no mock provider");
        };
    var select =
        channel == NotificationChannel.SMS
            ? "SELECT reference, customer_id, phone_number AS address, NULL AS title, text, sent_at"
            : "SELECT reference, customer_id, customer_id AS address, title, text, sent_at";
    var total =
        jdbc.sql("SELECT count(*) FROM " + table + " WHERE customer_id = :customerId")
            .param("customerId", customerId)
            .query(Long.class)
            .single();
    var items =
        jdbc.sql(
                select
                    + " FROM "
                    + table
                    + " WHERE customer_id = :customerId"
                    + " ORDER BY sent_at DESC, reference LIMIT :size OFFSET :offset")
            .param("customerId", customerId)
            .param("size", size)
            .param("offset", (long) page * size)
            .query(
                (rs, n) ->
                    new MockedMessage(
                        rs.getString("reference"),
                        channel,
                        rs.getString("customer_id"),
                        rs.getString("address"),
                        rs.getString("title"),
                        rs.getString("text"),
                        rs.getTimestamp("sent_at").toInstant()))
            .list();
    return new Page<>(items, page, size, total);
  }
}
