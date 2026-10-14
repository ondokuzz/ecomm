package com.ecomm.returnswarranty.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The Return Period and the Warranty Window, worked out from delivery: their ends at month ends and
 * leap days, and whether a request at a given time falls within them.
 */
class WindowTest {

  private static final Duration FOURTEEN_DAYS = Duration.ofDays(14);

  @Test
  void theReturnPeriodEndsItsLengthAfterDelivery() {
    var window = Window.returnPeriod(Instant.parse("2026-02-20T15:30:00Z"), FOURTEEN_DAYS);

    assertThat(window.kind()).isEqualTo(Window.Kind.RETURN_PERIOD);
    assertThat(window.startsAt()).isEqualTo("2026-02-20T15:30:00Z");
    assertThat(window.endsAt()).isEqualTo("2026-03-06T15:30:00Z");
    assertThat(window.warranty()).isNull();
  }

  @Test
  void theReturnPeriodCrossesALeapDay() {
    var window = Window.returnPeriod(Instant.parse("2028-02-20T08:00:00Z"), FOURTEEN_DAYS);

    assertThat(window.endsAt()).isEqualTo("2028-03-05T08:00:00Z");
  }

  @ParameterizedTest(name = "delivered {0}, {1} months: ends {2}")
  @CsvSource({
    // An ordinary month.
    "2026-03-15T10:00:00Z, 24, 2028-03-15T10:00:00Z",
    // A month end the later month doesn't have: the window ends on that month's last day.
    "2026-01-31T10:00:00Z, 1, 2026-02-28T10:00:00Z",
    "2027-01-31T10:00:00Z, 1, 2027-02-28T10:00:00Z",
    "2028-01-31T10:00:00Z, 1, 2028-02-29T10:00:00Z",
    "2026-08-31T23:59:59Z, 1, 2026-09-30T23:59:59Z",
    // A leap day, to a year without one, and to the next that has one.
    "2028-02-29T12:00:00Z, 12, 2029-02-28T12:00:00Z",
    "2028-02-29T12:00:00Z, 48, 2032-02-29T12:00:00Z",
    // Into a leap February from a month end.
    "2027-03-31T12:00:00Z, 11, 2028-02-29T12:00:00Z",
    // The longest Warranty terms.
    "2026-05-01T00:00:00Z, 120, 2036-05-01T00:00:00Z"
  })
  void theWarrantyWindowEndsTheWarrantysMonthsAfterDelivery(
      Instant deliveredAt, int months, Instant endsAt) {
    var terms = new WarrantyTerms(months, "Samsung");

    var window = Window.warrantyWindow(deliveredAt, terms);

    assertThat(window.kind()).isEqualTo(Window.Kind.WARRANTY_WINDOW);
    assertThat(window.startsAt()).isEqualTo(deliveredAt);
    assertThat(window.endsAt()).isEqualTo(endsAt);
    assertThat(window.warranty()).isEqualTo(terms);
  }

  @Test
  void aWindowIsOpenUntilTheInstantItEnds() {
    var window = Window.returnPeriod(Instant.parse("2026-02-20T15:30:00Z"), FOURTEEN_DAYS);

    assertThat(window.isOpenAt(Instant.parse("2026-02-20T15:30:00Z"))).isTrue();
    assertThat(window.isOpenAt(Instant.parse("2026-03-06T15:29:59.999999Z"))).isTrue();
    assertThat(window.isOpenAt(Instant.parse("2026-03-06T15:30:00Z"))).isFalse();
    assertThat(window.isOpenAt(Instant.parse("2026-04-01T00:00:00Z"))).isFalse();
  }

  @Test
  void aWarrantyWindowOverALeapDayIsOpenOnTheLastDay() {
    var window =
        Window.warrantyWindow(
            Instant.parse("2027-03-31T12:00:00Z"), new WarrantyTerms(11, "Apple"));

    assertThat(window.isOpenAt(Instant.parse("2028-02-29T11:59:59Z"))).isTrue();
    assertThat(window.isOpenAt(Instant.parse("2028-02-29T12:00:00Z"))).isFalse();
  }
}
