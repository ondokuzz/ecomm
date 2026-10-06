package com.ecomm.reviewsratings.domain;

import java.util.Locale;

/** How a review names its author: their given name and their family name's initial, capitalized. */
public final class DisplayName {

  private DisplayName() {}

  /** Such as "Demo C."; just one part when the other is missing, and "A Customer" without both. */
  public static String of(String givenName, String familyName) {
    var given = givenName == null ? "" : givenName.strip();
    var family = familyName == null ? "" : familyName.strip();
    var initial =
        family.isEmpty()
            ? ""
            : family.substring(0, family.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT) + ".";
    var name = (given + " " + initial).strip();
    return name.isEmpty() ? "A Customer" : name;
  }
}
