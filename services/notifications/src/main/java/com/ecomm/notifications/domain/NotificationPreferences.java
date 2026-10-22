package com.ecomm.notifications.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A Customer's Notification preferences: which channels besides email their messages go on. Email
 * is always on, since every message is about their own Orders, Payments and RMAs. SMS is off until
 * they save a mobile number in E.164 form and turn it on; push is off until they turn it on. {@code
 * updatedAt} is {@code null} for a Customer who never saved theirs.
 */
public record NotificationPreferences(
    String customerId, String phoneNumber, boolean sms, boolean push, Instant updatedAt) {

  /** E.164: a plus, a country code that doesn't start with 0, and at most 15 digits in all. */
  private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{1,14}");

  /** What a Customer who never saved theirs has: email only. */
  public static NotificationPreferences defaults(String customerId) {
    return new NotificationPreferences(customerId, null, false, false, null);
  }

  /**
   * The preferences a Customer saves. A blank {@code phoneNumber} is none; spaces in one are
   * dropped, so {@code +49 151 12345678} is {@code +4915112345678}.
   *
   * @throws InvalidPhoneNumberException for a number that isn't E.164, or SMS on without one
   */
  public static NotificationPreferences of(
      String customerId, String phoneNumber, boolean sms, boolean push, Instant now) {
    var number = phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.replace(" ", "");
    if (number != null && !E164.matcher(number).matches()) {
      throw new InvalidPhoneNumberException(
          "A mobile number is in international form: a plus, the country code, then the number,"
              + " such as +4915112345678");
    }
    if (sms && number == null) {
      throw new InvalidPhoneNumberException("SMS needs a mobile number");
    }
    return new NotificationPreferences(customerId, number, sms, push, now);
  }

  /** Email always; SMS and push when on and {@code type} goes on them. */
  public Set<NotificationChannel> channelsFor(MessageType type) {
    var channels = EnumSet.of(NotificationChannel.EMAIL);
    for (var channel : type.channels()) {
      if (allows(channel)) {
        channels.add(channel);
      }
    }
    return channels;
  }

  /** Whether messages may go on {@code channel}. */
  public boolean allows(NotificationChannel channel) {
    return switch (channel) {
      case EMAIL -> true;
      case SMS -> sms;
      case PUSH -> push;
    };
  }

  /** The mobile number refused, or SMS asked for without one. */
  public static class InvalidPhoneNumberException extends RuntimeException {

    public InvalidPhoneNumberException(String message) {
      super(message);
    }
  }
}
