package com.ecomm.returnswarranty.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * The time an RMA can be requested in, worked out from delivery: the Return Period for a return, or
 * the Warranty Window for a warranty claim. It is open from {@code startsAt} until just before
 * {@code endsAt}. An RMA keeps the window it was checked against, so a later change to the Return
 * Period or to a Product's Warranty terms doesn't change it.
 *
 * @param warranty the Warranty terms a Warranty Window was worked out from; null for a Return
 *     Period
 */
public record Window(Kind kind, Instant startsAt, Instant endsAt, WarrantyTerms warranty) {

  public enum Kind {
    RETURN_PERIOD,
    WARRANTY_WINDOW
  }

  /** The Return Period: {@code length} from delivery. */
  public static Window returnPeriod(Instant deliveredAt, Duration length) {
    return new Window(Kind.RETURN_PERIOD, deliveredAt, deliveredAt.plus(length), null);
  }

  /**
   * The Warranty Window: the warranty's months from delivery, counted on UTC's calendar. A day of
   * the month that the last month doesn't have, such as the 31st or a leap day, ends on that
   * month's last day.
   */
  public static Window warrantyWindow(Instant deliveredAt, WarrantyTerms warranty) {
    var endsAt = deliveredAt.atOffset(ZoneOffset.UTC).plusMonths(warranty.months()).toInstant();
    return new Window(Kind.WARRANTY_WINDOW, deliveredAt, endsAt, warranty);
  }

  /** Whether a request made {@code at} comes within it: before it ends. */
  public boolean isOpenAt(Instant at) {
    return at.isBefore(endsAt);
  }
}
