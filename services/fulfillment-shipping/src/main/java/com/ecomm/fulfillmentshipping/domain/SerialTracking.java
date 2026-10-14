package com.ecomm.fulfillmentshipping.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * How each unit of a Product's Variants is identified when it ships, as Catalog says: not at all,
 * by a serial number, or by an IMEI, the Serial number of a phone.
 */
public enum SerialTracking {
  NONE {
    @Override
    public Optional<String> normalise(String entered) {
      return Optional.empty();
    }

    @Override
    public String form() {
      return "takes no Serial number";
    }
  },

  /** 4 to 40 letters, digits and hyphens, kept upper-cased. */
  SERIAL {
    private static final Pattern FORM = Pattern.compile("[A-Za-z0-9-]{4,40}");

    @Override
    public Optional<String> normalise(String entered) {
      return entered != null && FORM.matcher(entered).matches()
          ? Optional.of(entered.toUpperCase(Locale.ROOT))
          : Optional.empty();
    }

    @Override
    public String form() {
      return "must be 4 to 40 letters, digits and hyphens";
    }
  },

  /** 15 digits, the last a Luhn check digit over the other 14. */
  IMEI {
    private static final Pattern FORM = Pattern.compile("[0-9]{15}");

    @Override
    public Optional<String> normalise(String entered) {
      return entered != null && FORM.matcher(entered).matches() && luhn(entered)
          ? Optional.of(entered)
          : Optional.empty();
    }

    @Override
    public String form() {
      return "must be an IMEI: 15 digits, the last a valid check digit";
    }
  };

  /** The Serial number as kept, or empty when {@code entered} isn't one of this kind. */
  public abstract Optional<String> normalise(String entered);

  /** What a Serial number of this kind must be, for a violation's message. */
  public abstract String form();

  /** Whether a unit of a Variant tracked this way needs a Serial number. */
  public boolean tracked() {
    return this != NONE;
  }

  /** Reads Catalog's value; a missing one, as in events from before Serial tracking, is NONE. */
  public static SerialTracking of(String value) {
    return value == null ? NONE : valueOf(value);
  }

  private static boolean luhn(String digits) {
    var sum = 0;
    for (var i = 0; i < digits.length(); i++) {
      var digit = digits.charAt(digits.length() - 1 - i) - '0';
      if (i % 2 == 1) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
    }
    return sum % 10 == 0;
  }
}
