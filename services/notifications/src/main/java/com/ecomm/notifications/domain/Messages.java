package com.ecomm.notifications.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** The model a message's templates are rendered against: its content's, and its Recipient's. */
public final class Messages {

  private Messages() {}

  /**
   * {@code content}'s model for {@code locale}, with {@code customer.givenName} and {@code
   * familyName}.
   */
  public static Map<String, Object> model(
      MessageContent content, Recipient recipient, Locale locale) {
    var model = new LinkedHashMap<>(content.model(locale));
    model.put(
        "customer",
        Map.of(
            "givenName", nullToEmpty(recipient.givenName()),
            "familyName", nullToEmpty(recipient.familyName())));
    return model;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
