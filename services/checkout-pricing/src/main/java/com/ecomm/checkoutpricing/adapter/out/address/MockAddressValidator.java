package com.ecomm.checkoutpricing.adapter.out.address;

import com.ecomm.checkoutpricing.application.port.out.AddressValidator;
import com.ecomm.checkoutpricing.domain.FieldViolation;
import com.ecomm.checkoutpricing.domain.InvalidShippingAddressException;
import com.ecomm.checkoutpricing.domain.ShippingAddress;
import com.ecomm.checkoutpricing.domain.UndeliverableCountryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The stand-in for a real address-validation service (ADR 0005). It knows the countries the store
 * ships to, the EU's 27, as the mock Carrier does, and each one's postcode format. It checks that
 * the required fields are there, that none is too long, that the postcode has its country's format
 * and that a phone looks like one. It normalises whitespace and case, and writes the postcode in
 * its country's usual form, such as {@code 1011 AB} for {@code 1011ab} in the Netherlands.
 */
@Component
class MockAddressValidator implements AddressValidator {

  private static final int MAX_LENGTH = 100;
  private static final int MAX_PHONE_LENGTH = 32;
  private static final Pattern PHONE = Pattern.compile("\\+?[0-9 ()-]+");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

  /**
   * Each country's postcode, as a template and an example: {@code 9} is a digit, {@code A} a
   * letter, {@code X} either, and anything else is written as it is. A template's leading literal
   * letters, such as Latvia's {@code LV-}, may be left out.
   */
  private record PostcodeFormat(String template, String example) {}

  private static final Map<String, PostcodeFormat> POSTCODES =
      Map.ofEntries(
          Map.entry("AT", new PostcodeFormat("9999", "1010")),
          Map.entry("BE", new PostcodeFormat("9999", "1000")),
          Map.entry("BG", new PostcodeFormat("9999", "1000")),
          Map.entry("HR", new PostcodeFormat("99999", "10000")),
          Map.entry("CY", new PostcodeFormat("9999", "1010")),
          Map.entry("CZ", new PostcodeFormat("999 99", "110 00")),
          Map.entry("DK", new PostcodeFormat("9999", "1050")),
          Map.entry("EE", new PostcodeFormat("99999", "10111")),
          Map.entry("FI", new PostcodeFormat("99999", "00100")),
          Map.entry("FR", new PostcodeFormat("99999", "75001")),
          Map.entry("DE", new PostcodeFormat("99999", "10115")),
          Map.entry("GR", new PostcodeFormat("999 99", "105 57")),
          Map.entry("HU", new PostcodeFormat("9999", "1011")),
          Map.entry("IE", new PostcodeFormat("AXX XXXX", "D02 X285")),
          Map.entry("IT", new PostcodeFormat("99999", "00118")),
          Map.entry("LV", new PostcodeFormat("LV-9999", "LV-1050")),
          Map.entry("LT", new PostcodeFormat("LT-99999", "LT-01100")),
          Map.entry("LU", new PostcodeFormat("9999", "1009")),
          Map.entry("MT", new PostcodeFormat("AAA 9999", "VLT 1117")),
          Map.entry("NL", new PostcodeFormat("9999 AA", "1011 AB")),
          Map.entry("PL", new PostcodeFormat("99-999", "00-950")),
          Map.entry("PT", new PostcodeFormat("9999-999", "1000-001")),
          Map.entry("RO", new PostcodeFormat("999999", "010011")),
          Map.entry("SK", new PostcodeFormat("999 99", "811 01")),
          Map.entry("SI", new PostcodeFormat("9999", "1000")),
          Map.entry("ES", new PostcodeFormat("99999", "28001")),
          Map.entry("SE", new PostcodeFormat("999 99", "111 22")));

  @Override
  public ShippingAddress validate(ShippingAddress address) {
    var country = upper(normalised(address.country()));
    var violations = new ArrayList<FieldViolation>();
    if (country == null) {
      violations.add(new FieldViolation("country", "is required"));
    } else if (!ISO_COUNTRIES.contains(country)) {
      violations.add(
          new FieldViolation("country", "must be an ISO 3166-1 alpha-2 code, such as DE"));
    } else if (!POSTCODES.containsKey(country)) {
      throw new UndeliverableCountryException(country);
    }

    var recipientName = required("recipientName", address.recipientName(), violations);
    var line1 = required("line1", address.line1(), violations);
    var line2 = optional("line2", address.line2(), violations);
    var postcode = postcode(address.postcode(), country, violations);
    var city = required("city", address.city(), violations);
    var region = optional("region", address.region(), violations);
    var phone = phone(address.phone(), violations);

    // A missing country was named above; listed in field order.
    violations.sort(
        (a, b) -> Integer.compare(FIELDS.indexOf(a.field()), FIELDS.indexOf(b.field())));
    if (!violations.isEmpty()) {
      throw new InvalidShippingAddressException(violations);
    }
    return new ShippingAddress(recipientName, line1, line2, postcode, city, region, country, phone);
  }

  private static final List<String> FIELDS =
      List.of("recipientName", "line1", "line2", "postcode", "city", "region", "country", "phone");

  private static String required(String field, String value, List<FieldViolation> violations) {
    var normalised = normalised(value);
    if (normalised == null) {
      violations.add(new FieldViolation(field, "is required"));
      return null;
    }
    return withinLength(field, normalised, MAX_LENGTH, violations);
  }

  private static String optional(String field, String value, List<FieldViolation> violations) {
    var normalised = normalised(value);
    return normalised == null ? null : withinLength(field, normalised, MAX_LENGTH, violations);
  }

  private static String withinLength(
      String field, String value, int maxLength, List<FieldViolation> violations) {
    if (value.length() > maxLength) {
      violations.add(new FieldViolation(field, "must be at most " + maxLength + " characters"));
    }
    return value;
  }

  /** The postcode in its country's usual form; only checked once the country is known. */
  private static String postcode(String value, String country, List<FieldViolation> violations) {
    var normalised = upper(normalised(value));
    if (normalised == null) {
      violations.add(new FieldViolation("postcode", "is required"));
      return null;
    }
    var format = country == null ? null : POSTCODES.get(country);
    if (format == null) {
      return normalised;
    }
    var formatted = formatted(normalised, format.template());
    if (formatted == null) {
      violations.add(
          new FieldViolation(
              "postcode", "isn't a postcode in " + country + ", such as " + format.example()));
      return normalised;
    }
    return formatted;
  }

  /** {@code postcode} written to {@code template}, or null when it doesn't fit it. */
  private static String formatted(String postcode, String template) {
    var compact = postcode.replaceAll("[\\s-]", "");
    var slots = template.replaceAll("[\\s-]", "");
    var prefix = new StringBuilder();
    for (var c : slots.toCharArray()) {
      if (c == '9' || c == 'A' || c == 'X') {
        break;
      }
      prefix.append(c);
    }
    if (!compact.startsWith(prefix.toString())) {
      compact = prefix + compact;
    }
    if (compact.length() != slots.length()) {
      return null;
    }
    for (var i = 0; i < slots.length(); i++) {
      var slot = slots.charAt(i);
      var c = compact.charAt(i);
      var fits =
          switch (slot) {
            case '9' -> c >= '0' && c <= '9';
            case 'A' -> c >= 'A' && c <= 'Z';
            case 'X' -> (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z');
            default -> c == slot;
          };
      if (!fits) {
        return null;
      }
    }
    var written = new StringBuilder();
    var next = 0;
    for (var slot : template.toCharArray()) {
      written.append(slot == ' ' || slot == '-' ? slot : compact.charAt(next++));
    }
    return written.toString();
  }

  private static String phone(String value, List<FieldViolation> violations) {
    var normalised = normalised(value);
    if (normalised == null) {
      return null;
    }
    if (normalised.length() > MAX_PHONE_LENGTH) {
      violations.add(
          new FieldViolation("phone", "must be at most " + MAX_PHONE_LENGTH + " characters"));
    } else if (!PHONE.matcher(normalised).matches()
        || normalised.chars().filter(Character::isDigit).count() < 5) {
      violations.add(new FieldViolation("phone", "must be a phone number, such as +49 30 1234567"));
    }
    return normalised;
  }

  /** Trimmed, with each run of whitespace inside it one space; null when nothing is left. */
  private static String normalised(String value) {
    if (value == null) {
      return null;
    }
    var collapsed = WHITESPACE.matcher(value.strip()).replaceAll(" ");
    return collapsed.isEmpty() ? null : collapsed;
  }

  private static String upper(String value) {
    return value == null ? null : value.toUpperCase(Locale.ROOT);
  }
}
