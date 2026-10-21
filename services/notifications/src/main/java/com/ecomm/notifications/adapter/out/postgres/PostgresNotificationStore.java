package com.ecomm.notifications.adapter.out.postgres;

import com.ecomm.notifications.application.port.in.NotificationFilter;
import com.ecomm.notifications.application.port.in.Page;
import com.ecomm.notifications.application.port.out.NotificationStore;
import com.ecomm.notifications.domain.MessageContent;
import com.ecomm.notifications.domain.MessageType;
import com.ecomm.notifications.domain.Notification;
import com.ecomm.notifications.domain.Notification.Attempt;
import com.ecomm.notifications.domain.NotificationChannel;
import com.ecomm.notifications.domain.NotificationStatus;
import com.ecomm.notifications.domain.OrderConfirmation;
import com.ecomm.notifications.domain.RenderedMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Notification ledger as rows of {@code notification}, with its attempts in {@code
 * notification_attempt}. Its content is kept as JSON of its Message type's record.
 */
@Component
class PostgresNotificationStore implements NotificationStore {

  private static final JsonMapper JSON =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private final JdbcClient jdbc;

  PostgresNotificationStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean recordIfNew(Notification n) {
    var inserted =
        jdbc.sql(
                """
                INSERT INTO notification (id, event_id, message_type, channel, customer_id,
                  order_id, rma_id, content, status, created_at, next_attempt_at)
                VALUES (:id, :eventId, :type, :channel, :customerId, :orderId, :rmaId,
                  CAST(:content AS JSONB), :status, :createdAt, :nextAttemptAt)
                ON CONFLICT (event_id, message_type, channel) DO NOTHING
                """)
            .param("id", n.id())
            .param("eventId", n.eventId())
            .param("type", n.type().name())
            .param("channel", n.channel().name())
            .param("customerId", n.customerId())
            .param("orderId", n.orderId())
            .param("rmaId", n.rmaId())
            .param("content", JSON.writeValueAsString(n.content()))
            .param("status", n.status().name())
            .param("createdAt", Timestamp.from(n.createdAt()))
            .param("nextAttemptAt", Timestamp.from(n.nextAttemptAt()))
            .update();
    return inserted == 1;
  }

  @Override
  public Optional<Notification> nextDue(Instant now) {
    return jdbc.sql(
            """
            SELECT * FROM notification WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
        .param("now", Timestamp.from(now))
        .query(this::notification)
        .optional();
  }

  @Override
  public void update(Notification n) {
    var message = n.message();
    jdbc.sql(
            """
            UPDATE notification SET status = :status, recipient = :recipient, subject = :subject,
              html_body = :html, text_body = :text, provider_reference = :reference,
              next_attempt_at = :nextAttemptAt, sent_at = :sentAt
            WHERE id = :id
            """)
        .param("id", n.id())
        .param("status", n.status().name())
        .param("recipient", n.recipient())
        .param("subject", message == null ? null : message.subject())
        .param("html", message == null ? null : message.html())
        .param("text", message == null ? null : message.text())
        .param("reference", n.providerReference())
        .param("nextAttemptAt", timestamp(n.nextAttemptAt()))
        .param("sentAt", timestamp(n.sentAt()))
        .update();
    for (var attempt : n.attempts()) {
      jdbc.sql(
              """
              INSERT INTO notification_attempt (notification_id, attempt, at, outcome, reason)
              VALUES (:id, :attempt, :at, :outcome, :reason)
              ON CONFLICT (notification_id, attempt) DO NOTHING
              """)
          .param("id", n.id())
          .param("attempt", attempt.number())
          .param("at", Timestamp.from(attempt.at()))
          .param("outcome", attempt.outcome().name())
          .param("reason", attempt.reason())
          .update();
    }
  }

  @Override
  public Page<Notification> find(NotificationFilter filter, int page, int size) {
    var column =
        switch (filter.field()) {
          case ORDER -> "order_id";
          case RMA -> "rma_id";
          case CUSTOMER -> "customer_id";
        };
    var total =
        jdbc.sql("SELECT count(*) FROM notification WHERE " + column + " = :value")
            .param("value", filter.value())
            .query(Long.class)
            .single();
    var items =
        jdbc.sql(
                "SELECT * FROM notification WHERE "
                    + column
                    + " = :value ORDER BY created_at DESC, id LIMIT :size OFFSET :offset")
            .param("value", filter.value())
            .param("size", size)
            .param("offset", (long) page * size)
            .query(this::notification)
            .list();
    return new Page<>(items, page, size, total);
  }

  Notification notification(ResultSet rs, int row) throws SQLException {
    var id = rs.getObject("id", UUID.class);
    var type = MessageType.valueOf(rs.getString("message_type"));
    var subject = rs.getString("subject");
    var html = rs.getString("html_body");
    var text = rs.getString("text_body");
    return new Notification(
        id,
        rs.getString("event_id"),
        NotificationChannel.valueOf(rs.getString("channel")),
        JSON.readValue(rs.getString("content"), contentOf(type)),
        rs.getTimestamp("created_at").toInstant(),
        attemptsOf(id),
        NotificationStatus.valueOf(rs.getString("status")),
        rs.getString("recipient"),
        subject == null && html == null && text == null
            ? null
            : new RenderedMessage(subject, html, text),
        rs.getString("provider_reference"),
        instant(rs.getTimestamp("next_attempt_at")),
        instant(rs.getTimestamp("sent_at")));
  }

  private List<Attempt> attemptsOf(UUID id) {
    return jdbc.sql(
            "SELECT * FROM notification_attempt WHERE notification_id = :id ORDER BY attempt")
        .param("id", id)
        .query(
            (rs, n) ->
                new Attempt(
                    rs.getInt("attempt"),
                    rs.getTimestamp("at").toInstant(),
                    Notification.Outcome.valueOf(rs.getString("outcome")),
                    rs.getString("reason")))
        .list();
  }

  private static Class<? extends MessageContent> contentOf(MessageType type) {
    return switch (type) {
      case ORDER_CONFIRMED -> OrderConfirmation.class;
    };
  }

  private static Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private static Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
