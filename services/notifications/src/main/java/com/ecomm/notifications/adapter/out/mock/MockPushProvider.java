package com.ecomm.notifications.adapter.out.mock;

import com.ecomm.notifications.application.port.out.NotificationProvider;
import com.ecomm.notifications.application.port.out.TimeSource;
import com.ecomm.notifications.domain.NotificationChannel;
import com.ecomm.notifications.domain.Recipient;
import com.ecomm.notifications.domain.RenderedMessage;
import com.ecomm.notifications.domain.SendOutcome;
import java.sql.Timestamp;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The push provider, mocked (ADR 0005): it needs no device and sends nothing, but records what it
 * would have pushed to the Customer in {@code mock_push_message}, for Staff to read, and logs it. A
 * real push service, with device registration, replaces it.
 */
@Component
class MockPushProvider implements NotificationProvider {

  private static final Logger log = LoggerFactory.getLogger(MockPushProvider.class);

  private final JdbcClient jdbc;
  private final TimeSource time;

  MockPushProvider(JdbcClient jdbc, TimeSource time) {
    this.jdbc = jdbc;
    this.time = time;
  }

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.PUSH;
  }

  @Override
  public SendOutcome send(Recipient recipient, RenderedMessage message) {
    var reference = "mock-push-" + UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO mock_push_message (reference, customer_id, title, text, sent_at)
            VALUES (:reference, :customerId, :title, :text, :sentAt)
            """)
        .param("reference", reference)
        .param("customerId", recipient.customerId())
        .param("title", message.subject())
        .param("text", message.text())
        .param("sentAt", Timestamp.from(time.now()))
        .update();
    log.info(
        "Mock push {} to Customer {}: {}: {}",
        reference,
        recipient.customerId(),
        message.subject(),
        message.text());
    return new SendOutcome.Sent(reference);
  }
}
