package com.ecomm.notifications.domain;

import java.util.Locale;

/**
 * Who a Notification goes to: the Customer's email address and names, as Identity & Access holds
 * them, the locale they read in, and the mobile number their Notification preferences hold, or
 * {@code null}.
 */
public record Recipient(
    String customerId,
    String email,
    String givenName,
    String familyName,
    Locale locale,
    String phoneNumber) {

  /** The made-up Customer previews and the start-up check render templates for. */
  public static final Recipient SAMPLE =
      new Recipient(
          "sample-customer",
          "ada@example.com",
          "Ada",
          "Lovelace",
          Locale.ENGLISH,
          "+4915112345678");

  /** The Customer as the directory knows them, before their mobile number is. */
  public Recipient(
      String customerId, String email, String givenName, String familyName, Locale locale) {
    this(customerId, email, givenName, familyName, locale, null);
  }

  public Recipient withPhoneNumber(String phoneNumber) {
    return new Recipient(customerId, email, givenName, familyName, locale, phoneNumber);
  }

  /**
   * Where a message on {@code channel} goes: the email address, the mobile number ({@code null}
   * without one), or for push the Customer, whose devices a push service knows.
   */
  public String addressOn(NotificationChannel channel) {
    return switch (channel) {
      case EMAIL -> email;
      case SMS -> phoneNumber;
      case PUSH -> customerId;
    };
  }
}
