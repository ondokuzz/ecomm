package com.ecomm.notifications.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One message on one Notification channel to one Customer, triggered by one event, as the
 * Notification ledger keeps it. It is recorded {@code PENDING}, at most once per event, Message
 * type and channel, then sent: {@code SENT} with the provider's reference, or {@code FAILED} once a
 * permanent failure or the {@link RetryPolicy}'s last attempt says so. Every attempt is kept, with
 * the Recipient's address and the message as last rendered.
 */
public final class Notification {

  /** One try at sending it: when, and what came of it. */
  public record Attempt(int number, Instant at, Outcome outcome, String reason) {}

  public enum Outcome {
    SENT,
    TRANSIENT_FAILURE,
    PERMANENT_FAILURE
  }

  private final UUID id;
  private final String eventId;
  private final NotificationChannel channel;
  private final MessageContent content;
  private final Instant createdAt;
  private final List<Attempt> attempts;
  private NotificationStatus status;
  private String recipient;
  private RenderedMessage message;
  private String providerReference;
  private Instant nextAttemptAt;
  private Instant sentAt;

  public Notification(
      UUID id,
      String eventId,
      NotificationChannel channel,
      MessageContent content,
      Instant createdAt,
      List<Attempt> attempts,
      NotificationStatus status,
      String recipient,
      RenderedMessage message,
      String providerReference,
      Instant nextAttemptAt,
      Instant sentAt) {
    this.id = id;
    this.eventId = eventId;
    this.channel = channel;
    this.content = content;
    this.createdAt = createdAt;
    this.attempts = new ArrayList<>(attempts);
    this.status = status;
    this.recipient = recipient;
    this.message = message;
    this.providerReference = providerReference;
    this.nextAttemptAt = nextAttemptAt;
    this.sentAt = sentAt;
  }

  /** A new {@code PENDING} Notification of {@code content}, due at once. */
  public static Notification record(
      String eventId, NotificationChannel channel, MessageContent content, Instant now) {
    return new Notification(
        UUID.randomUUID(),
        eventId,
        channel,
        content,
        now,
        List.of(),
        NotificationStatus.PENDING,
        null,
        null,
        null,
        now,
        null);
  }

  /** The provider took it. */
  public void sent(Instant at, Recipient to, RenderedMessage rendered, String reference) {
    requirePending();
    attempts.add(new Attempt(attempts.size() + 1, at, Outcome.SENT, null));
    recipient = to.email();
    message = rendered;
    providerReference = reference;
    status = NotificationStatus.SENT;
    nextAttemptAt = null;
    sentAt = at;
  }

  /**
   * It wasn't sent. A transient failure is tried again when {@code policy} allows another attempt;
   * otherwise, and after a permanent one, it is {@code FAILED}. {@code to} and {@code rendered} are
   * {@code null} when the failure came before them.
   */
  public void failed(
      Instant at,
      Recipient to,
      RenderedMessage rendered,
      SendOutcome.Failed failure,
      RetryPolicy policy) {
    requirePending();
    var outcome = failure.isTransient() ? Outcome.TRANSIENT_FAILURE : Outcome.PERMANENT_FAILURE;
    attempts.add(new Attempt(attempts.size() + 1, at, outcome, failure.reason()));
    if (to != null) {
      recipient = to.email();
    }
    if (rendered != null) {
      message = rendered;
    }
    var retryAfter =
        failure.isTransient() ? policy.retryAfter(attempts.size()) : Optional.<Duration>empty();
    if (retryAfter.isPresent()) {
      nextAttemptAt = at.plus(retryAfter.get());
    } else {
      status = NotificationStatus.FAILED;
      nextAttemptAt = null;
    }
  }

  private void requirePending() {
    if (status != NotificationStatus.PENDING) {
      throw new IllegalStateException("Notification " + id + " is " + status + ", not PENDING");
    }
  }

  public UUID id() {
    return id;
  }

  public String eventId() {
    return eventId;
  }

  public MessageType type() {
    return content.type();
  }

  public NotificationChannel channel() {
    return channel;
  }

  public MessageContent content() {
    return content;
  }

  public String customerId() {
    return content.customerId();
  }

  public String orderId() {
    return content.orderId();
  }

  public String rmaId() {
    return content.rmaId();
  }

  public Instant createdAt() {
    return createdAt;
  }

  public List<Attempt> attempts() {
    return List.copyOf(attempts);
  }

  public NotificationStatus status() {
    return status;
  }

  /** The address it was last sent or tried to, or {@code null} before one was known. */
  public String recipient() {
    return recipient;
  }

  /** The message as last rendered, or {@code null} before it was. */
  public RenderedMessage message() {
    return message;
  }

  public String providerReference() {
    return providerReference;
  }

  /** When it is next tried, while {@code PENDING}. */
  public Instant nextAttemptAt() {
    return nextAttemptAt;
  }

  public Instant sentAt() {
    return sentAt;
  }
}
