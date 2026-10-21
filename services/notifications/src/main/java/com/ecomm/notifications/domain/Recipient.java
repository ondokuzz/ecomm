package com.ecomm.notifications.domain;

import java.util.Locale;

/**
 * Who a Notification goes to: the Customer's email address and names, as Identity & Access holds
 * them, and the locale they read in.
 */
public record Recipient(
    String customerId, String email, String givenName, String familyName, Locale locale) {

  /** The made-up Customer previews and the start-up check render templates for. */
  public static final Recipient SAMPLE =
      new Recipient("sample-customer", "ada@example.com", "Ada", "Lovelace", Locale.ENGLISH);

  /** The email address with all but the first character of its local part hidden. */
  public String maskedEmail() {
    return mask(email);
  }

  /** Such as {@code a***@example.com}; {@code null} stays {@code null}. */
  public static String mask(String email) {
    if (email == null) {
      return null;
    }
    var at = email.lastIndexOf('@');
    if (at <= 0) {
      return "***";
    }
    return email.charAt(0) + "***" + email.substring(at);
  }
}
