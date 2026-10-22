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
 * The SMS provider, mocked (ADR 0005): it sends nothing, but records what it would have sent in
 * {@code mock_sms_message}, for Staff to read, and logs it. A real SMS gateway replaces it. It
 * records in the dispatcher's transaction, so a send rolled back is forgotten too.
 */
@Component
class MockSmsProvider implements NotificationProvider {

  private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

  private final JdbcClient jdbc;
  private final TimeSource time;

  MockSmsProvider(JdbcClient jdbc, TimeSource time) {
    this.jdbc = jdbc;
    this.time = time;
  }

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.SMS;
  }

  @Override
  public SendOutcome send(Recipient recipient, RenderedMessage message) {
    var reference = "mock-sms-" + UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO mock_sms_message (reference, customer_id, phone_number, text, sent_at)
            VALUES (:reference, :customerId, :phoneNumber, :text, :sentAt)
            """)
        .param("reference", reference)
        .param("customerId", recipient.customerId())
        .param("phoneNumber", recipient.phoneNumber())
        .param("text", message.text())
        .param("sentAt", Timestamp.from(time.now()))
        .update();
    log.info(
        "Mock SMS {} to {}: {}",
        reference,
        NotificationChannel.SMS.mask(recipient.phoneNumber()),
        message.text());
    return new SendOutcome.Sent(reference);
  }
}
