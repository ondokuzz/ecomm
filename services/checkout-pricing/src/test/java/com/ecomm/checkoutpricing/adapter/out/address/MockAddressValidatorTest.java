package com.ecomm.checkoutpricing.adapter.out.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.checkoutpricing.domain.FieldViolation;
import com.ecomm.checkoutpricing.domain.InvalidShippingAddressException;
import com.ecomm.checkoutpricing.domain.ShippingAddress;
import com.ecomm.checkoutpricing.domain.UndeliverableCountryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The mock {@code AddressValidator}, through its port: each supported country's postcode format,
 * the required fields and their lengths, and the address it hands back normalised. Pure JUnit, a
 * deliberate exception to testing at the HTTP seam, for a table of countries.
 */
class MockAddressValidatorTest {

  private final MockAddressValidator validator = new MockAddressValidator();

  private static ShippingAddress in(String country, String postcode) {
    return new ShippingAddress(
        "Erika Mustermann", "Hauptstraße 5", null, postcode, "Berlin", null, country, null);
  }

  @ParameterizedTest(name = "{0} {1} is {2}")
  @CsvSource({
    "DE, 10115, 10115",
    "DE, ' 10115 ', 10115",
    "NL, 1011 AB, 1011 AB",
    "NL, 1011ab, 1011 AB",
    "NL, '1011   ab', 1011 AB",
    "FR, 75001, 75001",
    "AT, 1010, 1010",
    "BE, 1000, 1000",
    "BG, 1000, 1000",
    "HR, 10000, 10000",
    "CY, 1010, 1010",
    "CZ, 110 00, 110 00",
    "CZ, 11000, 110 00",
    "DK, 1050, 1050",
    "EE, 10111, 10111",
    "FI, 00100, 00100",
    "GR, 105 57, 105 57",
    "GR, 10557, 105 57",
    "HU, 1011, 1011",
    "IE, D02 X285, D02 X285",
    "IE, d02x285, D02 X285",
    "IT, 00118, 00118",
    "LV, LV-1050, LV-1050",
    "LV, 1050, LV-1050",
    "LT, LT-01100, LT-01100",
    "LT, 01100, LT-01100",
    "LU, 1009, 1009",
    "MT, VLT 1117, VLT 1117",
    "MT, vlt1117, VLT 1117",
    "PL, 00-950, 00-950",
    "PL, 00950, 00-950",
    "PT, 1000-001, 1000-001",
    "PT, 1000001, 1000-001",
    "RO, 010011, 010011",
    "SK, 811 01, 811 01",
    "SI, 1000, 1000",
    "ES, 28001, 28001",
    "SE, 111 22, 111 22",
    "SE, 11122, 111 22",
  })
  void aValidPostcodeComesBackInItsUsualForm(String country, String postcode, String normalised) {
    assertThat(validator.validate(in(country, postcode)).postcode()).isEqualTo(normalised);
  }

  @ParameterizedTest(name = "{0} {1} is refused")
  @CsvSource({
    "DE, 1011",
    "DE, 101155",
    "DE, 1011A",
    "NL, 1011",
    "NL, 1011 A1",
    "NL, AB 1011",
    "FR, 7500",
    "AT, 10100",
    "CZ, 1100",
    "IE, 12345",
    "LV, LT-1050",
    "PL, 00-95",
    "PT, 1000-01",
    "MT, VL 1117",
    "RO, 01001",
    "SE, 1112",
  })
  void aPostcodeInTheWrongFormatForItsCountryIsNamed(String country, String postcode) {
    assertThatThrownBy(() -> validator.validate(in(country, postcode)))
        .isInstanceOfSatisfying(
            InvalidShippingAddressException.class,
            e ->
                assertThat(e.violations())
                    .extracting(FieldViolation::field)
                    .containsExactly("postcode"));
  }

  @Test
  void everyFieldIsTrimmedAndItsInnerSpacesCollapsedAndBlankOptionalFieldsAreLeftOut() {
    var validated =
        validator.validate(
            new ShippingAddress(
                "  Erika   Mustermann ",
                " Hauptstraße  5",
                "   ",
                "10115",
                " Berlin ",
                "",
                "de",
                " +49 30  1234567 "));

    assertThat(validated)
        .isEqualTo(
            new ShippingAddress(
                "Erika Mustermann",
                "Hauptstraße 5",
                null,
                "10115",
                "Berlin",
                null,
                "DE",
                "+49 30 1234567"));
  }

  @Test
  void eachMissingRequiredFieldIsNamed() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    new ShippingAddress(" ", null, null, "", null, null, "DE", null)))
        .isInstanceOfSatisfying(
            InvalidShippingAddressException.class,
            e ->
                assertThat(e.violations())
                    .extracting(FieldViolation::field)
                    .containsExactly("recipientName", "line1", "postcode", "city"));
  }

  @Test
  void aFieldTooLongIsNamedWithItsLimit() {
    var tooLong = "x".repeat(101);
    assertThatThrownBy(
            () ->
                validator.validate(
                    new ShippingAddress(
                        tooLong,
                        "Hauptstraße 5",
                        tooLong,
                        "10115",
                        "Berlin",
                        tooLong,
                        "DE",
                        "+49 " + "1".repeat(30))))
        .isInstanceOfSatisfying(
            InvalidShippingAddressException.class,
            e ->
                assertThat(e.violations())
                    .containsExactly(
                        new FieldViolation("recipientName", "must be at most 100 characters"),
                        new FieldViolation("line2", "must be at most 100 characters"),
                        new FieldViolation("region", "must be at most 100 characters"),
                        new FieldViolation("phone", "must be at most 32 characters")));
  }

  @Test
  void aPhoneThatIsntOneIsNamed() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    new ShippingAddress(
                        "Erika Mustermann",
                        "Hauptstraße 5",
                        null,
                        "10115",
                        "Berlin",
                        null,
                        "DE",
                        "call me")))
        .isInstanceOfSatisfying(
            InvalidShippingAddressException.class,
            e ->
                assertThat(e.violations())
                    .extracting(FieldViolation::field)
                    .containsExactly("phone"));
  }

  @Test
  void aMissingOrMalformedCountryIsNamed() {
    for (var country : new String[] {null, "", "Germany", "XX"}) {
      assertThatThrownBy(() -> validator.validate(in(country, "10115")))
          .isInstanceOfSatisfying(
              InvalidShippingAddressException.class,
              e ->
                  assertThat(e.violations())
                      .extracting(FieldViolation::field)
                      .containsExactly("country"));
    }
  }

  @Test
  void aCountryTheStoreDoesntShipToIsUndeliverableWhateverElseIsWrong() {
    for (var country : new String[] {"US", "CH", "GB", "NO"}) {
      assertThatThrownBy(
              () ->
                  validator.validate(
                      new ShippingAddress(null, null, null, null, null, null, country, null)))
          .isInstanceOfSatisfying(
              UndeliverableCountryException.class, e -> assertThat(e.country()).isEqualTo(country));
    }
  }
}
