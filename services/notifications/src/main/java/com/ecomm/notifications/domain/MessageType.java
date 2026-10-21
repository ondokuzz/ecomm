package com.ecomm.notifications.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * What a Notification says, and so the Message templates it is rendered from: one per Notification
 * channel and locale. Each has sample content, which its templates must render at start-up and
 * which Staff preview them with.
 */
public enum MessageType {
  /** An Order's change to {@code PAID}: what was bought, and what was paid. */
  ORDER_CONFIRMED(
      () ->
          new OrderConfirmation(
              "3f2a9c1b-7d4e-4f0a-9b8c-2d1e0f3a4b5c",
              "sample-customer",
              Instant.parse("2026-10-21T09:30:00Z"),
              List.of(
                  new OrderConfirmation.Line("Google Pixel 9 (128 GB, Obsidian)", 1, eur(79900)),
                  new OrderConfirmation.Line("Sony WH-1000XM5 (Black)", 2, eur(37900))),
              eur(155700),
              List.of(eur(15570)),
              eur(499),
              eur(0),
              eur(140629)));

  private final Supplier<MessageContent> sample;

  MessageType(Supplier<MessageContent> sample) {
    this.sample = sample;
  }

  /** Content of this type made up for previews and the start-up check. */
  public MessageContent sample() {
    return sample.get();
  }

  private static Money eur(long amountMinor) {
    return Money.of(amountMinor, "EUR");
  }
}
