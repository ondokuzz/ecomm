package com.ecomm.notifications.domain;

/**
 * What a {@code NotificationProvider} answers: the provider's reference for a message it took, or
 * why it didn't. A transient failure may succeed later, so it is retried; a permanent one never
 * will.
 */
public sealed interface SendOutcome {

  record Sent(String reference) implements SendOutcome {}

  record Failed(boolean isTransient, String reason) implements SendOutcome {

    public static Failed transientFailure(String reason) {
      return new Failed(true, reason);
    }

    public static Failed permanent(String reason) {
      return new Failed(false, reason);
    }
  }
}
