package com.ecomm.supportassistant.application.port.out;

import java.time.Duration;

/**
 * The Conversations whose turns are running, each claimed by its running message, shared by every
 * instance: so a Conversation runs one turn at a time, and a repeat of a message sent while it is
 * still being answered runs none of its own.
 */
public interface TurnsInFlight {

  /**
   * Claims the Conversation for the turn of the message sent with {@code idempotencyKey}, or with
   * none when it is null, unless another turn holds it. A claim not released lapses after {@code
   * atMost}, should its instance stop mid-turn.
   */
  Claim claim(String conversationId, String idempotencyKey, Duration atMost);

  /**
   * Releases the claim, if it is still the Conversation's: not once it lapsed and another took it.
   */
  void release(Claim.Held claim);

  /** What a claim came to. */
  sealed interface Claim {

    /** The turn holds its Conversation, as {@code holder}, until it releases it. */
    record Held(String conversationId, String holder) implements Claim {}

    /** A turn of a message with the same {@code Idempotency-Key} holds it. */
    record HeldBySameKey() implements Claim {}

    /** A turn of another message holds it. */
    record HeldByAnother() implements Claim {}
  }
}
