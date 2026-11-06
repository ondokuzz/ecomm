package com.ecomm.supportassistant.application.port.out;

import java.time.Duration;

/**
 * The messages whose turns are running, by their Conversation and {@code Idempotency-Key}, shared
 * by every instance: so a repeat sent while the first is still being answered can't run a turn of
 * its own.
 */
public interface TurnsInFlight {

  /**
   * Claims the key for a turn, unless another holds it: whether it did. A claim not released lapses
   * after {@code atMost}, should its instance stop mid-turn.
   */
  boolean claim(String conversationId, String idempotencyKey, Duration atMost);

  void release(String conversationId, String idempotencyKey);
}
