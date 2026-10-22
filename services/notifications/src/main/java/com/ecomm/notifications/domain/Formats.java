package com.ecomm.notifications.domain;

import com.ecomm.commons.money.Money;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** How a message writes Money, dates and references for its locale. */
final class Formats {

  private Formats() {}

  /** Such as {@code €1,598.00} in English: by the Currency's Minor unit, never through doubles. */
  static String money(Money money, Locale locale) {
    var format = NumberFormat.getCurrencyInstance(locale);
    var digits = money.currency().getDefaultFractionDigits();
    format.setCurrency(money.currency());
    format.setMinimumFractionDigits(digits);
    format.setMaximumFractionDigits(digits);
    return format.format(BigDecimal.valueOf(money.amountMinor(), digits));
  }

  /** Such as {@code October 21, 2026} in English, on the UTC calendar. */
  static String date(Instant instant, Locale locale) {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        .withLocale(locale)
        .format(instant.atZone(ZoneOffset.UTC));
  }

  /** The Order reference: {@code #} and the first eight characters of its ID, upper-cased. */
  static String orderReference(String orderId) {
    return "#" + orderId.substring(0, Math.min(8, orderId.length())).toUpperCase(Locale.ROOT);
  }

  /** The RMA reference: {@code RMA-} and the first eight characters of its ID, upper-cased. */
  static String rmaReference(String rmaId) {
    return "RMA-" + rmaId.substring(0, Math.min(8, rmaId.length())).toUpperCase(Locale.ROOT);
  }
}
