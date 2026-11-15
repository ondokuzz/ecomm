package com.ecomm.notifications.application.port.in;

/**
 * Turning cart reminders off from a reminder's unsubscribe link, without signing in: the link's
 * signed token names the Customer.
 */
public interface UnsubscribeUseCase {

  /** Whether {@code token} is one this service signed, for cart reminders, and not expired. */
  boolean isValid(String token);

  /**
   * Turns off the cart reminders of the Customer {@code token} names. Doing it again changes
   * nothing.
   *
   * @throws InvalidTokenException for a token {@link #isValid} refuses
   */
  void unsubscribeFromCartReminders(String token);

  /** A token this service didn't sign, one changed since, or one expired. */
  class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
      super("The unsubscribe link is invalid or has expired");
    }
  }
}
