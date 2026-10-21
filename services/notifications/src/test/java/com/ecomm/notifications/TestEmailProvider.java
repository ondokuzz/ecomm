package com.ecomm.notifications;

import static org.awaitility.Awaitility.await;

import com.ecomm.notifications.application.port.out.NotificationProvider;
import com.ecomm.notifications.domain.NotificationChannel;
import com.ecomm.notifications.domain.Recipient;
import com.ecomm.notifications.domain.RenderedMessage;
import com.ecomm.notifications.domain.SendOutcome;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only: the email {@link NotificationProvider} as a double. It keeps every message it is
 * handed, and answers a reference, unless a test has told it to fail for an address.
 */
class TestEmailProvider implements NotificationProvider {

  record Sent(Recipient recipient, RenderedMessage message) {}

  private final List<Sent> sent = new CopyOnWriteArrayList<>();
  private final Map<String, SendOutcome> failures = new ConcurrentHashMap<>();
  private final AtomicInteger references = new AtomicInteger();

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.EMAIL;
  }

  @Override
  public SendOutcome send(Recipient recipient, RenderedMessage message) {
    sent.add(new Sent(recipient, message));
    var failure = failures.get(recipient.email());
    return failure != null ? failure : new SendOutcome.Sent("test-" + references.incrementAndGet());
  }

  /** Every send to {@code email} fails so from now on. */
  void failFor(String email, SendOutcome.Failed failure) {
    failures.put(email, failure);
  }

  /** What was handed over for {@code email}, oldest first. */
  List<Sent> sentTo(String email) {
    return sent.stream().filter(s -> email.equals(s.recipient().email())).toList();
  }

  /** The one message sent to {@code email}, once it has been. */
  Sent awaitOneSentTo(String email) {
    await().atMost(Duration.ofSeconds(20)).until(() -> !sentTo(email).isEmpty());
    return sentTo(email).getFirst();
  }
}
