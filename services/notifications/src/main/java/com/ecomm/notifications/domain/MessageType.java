package com.ecomm.notifications.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What a Notification says, and so the Message templates it is rendered from: one per Notification
 * channel it goes on and locale. Email is always one of them; SMS and push are for the messages a
 * Customer would want on their phone, and are sent only when their Notification preferences opt in;
 * an RMA's steps go by email alone. Each has sample content, which its templates must render at
 * start-up and which Staff preview them with.
 */
public enum MessageType {
  /** An Order's change to {@code PAID}: what was bought, and what was paid. */
  ORDER_CONFIRMED(
      Samples.EMAIL,
      () ->
          new OrderConfirmation(
              Samples.ORDER_ID,
              Samples.CUSTOMER_ID,
              Instant.parse("2026-10-21T09:30:00Z"),
              List.of(
                  new OrderConfirmation.Line("Google Pixel 9 (128 GB, Obsidian)", 1, eur(79900)),
                  new OrderConfirmation.Line("Sony WH-1000XM5 (Black)", 2, eur(37900))),
              eur(155700),
              List.of(eur(15570)),
              eur(499),
              eur(0),
              eur(140629))),
  /** An Order's Shipment handed to its Carrier, with its tracking number. */
  ORDER_SHIPPED(
      Samples.PHONE_CHANNELS,
      () ->
          new OrderShipped(
              Samples.ORDER_ID,
              Samples.CUSTOMER_ID,
              "Mock Carrier",
              "MC0012345678",
              Samples.SHIPPED_LINES)),
  /** An Order's Shipment delivered. */
  ORDER_DELIVERED(
      Samples.PHONE_CHANNELS,
      () ->
          new OrderDelivered(
              Samples.ORDER_ID,
              Samples.CUSTOMER_ID,
              Instant.parse("2026-10-23T14:05:00Z"),
              Samples.SHIPPED_LINES)),
  /** The replacement for an RMA's units handed to its Carrier. */
  REPLACEMENT_SHIPPED(
      Samples.PHONE_CHANNELS,
      () ->
          new ReplacementShipped(
              Samples.ORDER_ID,
              "7c4b2e1a-9f3d-4a8b-b6c5-0d1e2f3a4b5c",
              Samples.CUSTOMER_ID,
              "Mock Carrier",
              "MC0087654321",
              List.of(new ShippedLine("Google Pixel 9 (128 GB, Obsidian)", 1)))),
  /** An approved Refund of an Order's Payment. */
  PAYMENT_REFUNDED(
      Samples.PHONE_CHANNELS,
      () ->
          new PaymentRefunded(
              Samples.ORDER_ID,
              Samples.CUSTOMER_ID,
              eur(79900),
              "RMA-7C4B2E1A",
              Instant.parse("2026-10-30T11:00:00Z"))),
  /** A Customer's RMA requested, for Staff to approve or reject. */
  RMA_REQUESTED(Samples.EMAIL, () -> new RmaRequested(RmaSummary.sample("RETURN"))),
  /** An RMA approved: where to send its units back, and by when. */
  RMA_APPROVED(
      Samples.EMAIL,
      () ->
          new RmaApproved(
              RmaSummary.sample("RETURN"),
              new ReturnAddress("Ecomm Returns Berlin", "Lagerstraße 1", "10115", "Berlin", "DE"),
              Instant.parse("2026-11-06T12:05:00Z"))),
  /** An RMA rejected, with Staff's reason. */
  RMA_REJECTED(
      Samples.EMAIL,
      () ->
          new RmaRejected(
              RmaSummary.sample("WARRANTY_CLAIM"),
              "The damage to the screen isn't covered by the manufacturer's warranty.")),
  /** An RMA's units received at its Warehouse, to be inspected. */
  RMA_RECEIVED(Samples.EMAIL, () -> new RmaReceived(RmaSummary.sample("RETURN"))),
  /** An RMA closed after an Inspection refused it, with the Inspection's note. */
  RMA_CLOSED(
      Samples.EMAIL,
      () ->
          new RmaClosed(
              RmaSummary.sample("RETURN"), "The seal is broken and the charger is missing.")),
  /** An RMA whose units weren't received by its Send-back deadline. */
  RMA_EXPIRED(
      Samples.EMAIL,
      () -> new RmaExpired(RmaSummary.sample("RETURN"), Instant.parse("2026-11-06T12:05:00Z")));

  private final Set<NotificationChannel> channels;
  private final Supplier<MessageContent> sample;

  MessageType(Set<NotificationChannel> channels, Supplier<MessageContent> sample) {
    this.channels = channels;
    this.sample = sample;
  }

  /** The channels it can go on, each with its templates, in the channels' order. */
  public Set<NotificationChannel> channels() {
    return EnumSet.copyOf(channels);
  }

  /** Content of this type made up for previews and the start-up check. */
  public MessageContent sample() {
    return sample.get();
  }

  private static Money eur(long amountMinor) {
    return Money.of(amountMinor, "EUR");
  }

  /** What the samples share: an enum's constants can't refer to its static fields. */
  private static final class Samples {
    static final String ORDER_ID = "3f2a9c1b-7d4e-4f0a-9b8c-2d1e0f3a4b5c";
    static final String CUSTOMER_ID = "sample-customer";
    static final Set<NotificationChannel> EMAIL = EnumSet.of(NotificationChannel.EMAIL);
    static final Set<NotificationChannel> PHONE_CHANNELS =
        EnumSet.of(NotificationChannel.EMAIL, NotificationChannel.SMS, NotificationChannel.PUSH);
    static final List<ShippedLine> SHIPPED_LINES =
        List.of(
            new ShippedLine("Google Pixel 9 (128 GB, Obsidian)", 1),
            new ShippedLine("Sony WH-1000XM5 (Black)", 2));
  }
}
