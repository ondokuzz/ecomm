package com.ecomm.fulfillmentshipping.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** What Staff may enter for a unit: an IMEI with its Luhn check digit, or a serial number. */
class SerialNumberTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "490154203237518", // the IMEI of the GSMA's own example
        "356938035643809",
        "000000000000000"
      })
  void anImeiWithAValidCheckDigitIsAccepted(String imei) {
    assertThat(SerialTracking.IMEI.normalise(imei)).contains(imei);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "490154203237517", // the last digit is off by one
        "49015420323751", // 14 digits
        "4901542032375180", // 16 digits
        "49015420323751A",
        " 490154203237518",
        ""
      })
  void anImeiThatFailsItsCheckOrItsFormIsRefused(String imei) {
    assertThat(SerialTracking.IMEI.normalise(imei)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "abcd, ABCD",
    "SN-2026-0001, SN-2026-0001",
    "c02xk1zyjgh5, C02XK1ZYJGH5",
    "0123456789012345678901234567890123456789, 0123456789012345678901234567890123456789"
  })
  void aSerialNumberIsUpperCased(String entered, String kept) {
    assertThat(SerialTracking.SERIAL.normalise(entered)).contains(kept);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "abc", // 3 characters
        "01234567890123456789012345678901234567890", // 41 characters
        "SN 0001",
        "SN_0001",
        "SNÖ0001",
        ""
      })
  void aSerialNumberOfTheWrongFormIsRefused(String entered) {
    assertThat(SerialTracking.SERIAL.normalise(entered)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"490154203237518", "SN-0001"})
  void aVariantThatIsNotTrackedTakesNoSerialNumber(String entered) {
    assertThat(SerialTracking.NONE.normalise(entered)).isEmpty();
  }
}
