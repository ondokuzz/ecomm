package com.ecomm.returnswarranty.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.returnswarranty.domain.RmaExceptions.NoWarrantyException;
import com.ecomm.returnswarranty.domain.RmaExceptions.OutsideWindowException;
import com.ecomm.returnswarranty.domain.RmaExceptions.QuantityExceededException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumberAlreadyClaimedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumberNotShippedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumbersMiscountedException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Requesting an RMA against a delivered Shipment: how many units are still free to claim and which
 * Serial numbers, given the Customer's other RMAs, and the window the request is checked against.
 * The clock is fixed.
 */
class RmaRequestTest {

  private static final Instant DELIVERED_AT = Instant.parse("2026-03-01T10:00:00Z");
  private static final Instant NOW = Instant.parse("2026-03-05T10:00:00Z");
  private static final Duration RETURN_PERIOD = Duration.ofDays(14);
  private static final Optional<WarrantyTerms> TWO_YEARS =
      Optional.of(new WarrantyTerms(24, "Samsung"));

  private static final String IMEI_1 = "490154203237518";
  private static final String IMEI_2 = "356938035643809";
  private static final String IMEI_3 = "353918056987651";

  /** Three phones, tracked by IMEI, two laptops by serial number, and four untracked cases. */
  private static final Delivery DELIVERY =
      new Delivery(
          UUID.randomUUID(),
          "order-1",
          "customer-42",
          DELIVERED_AT,
          List.of(
              new DeliveredLine(
                  "PHONE", "Galaxy S25", 3, SerialTracking.IMEI, List.of(IMEI_1, IMEI_2, IMEI_3)),
              new DeliveredLine(
                  "LAPTOP", "ThinkPad X1", 2, SerialTracking.SERIAL, List.of("SN-A1", "SN-B2")),
              new DeliveredLine("CASE", "Phone case", 4, SerialTracking.NONE, List.of())));

  private final List<Rma> others = new ArrayList<>();

  @Test
  void aReturnWithinTheReturnPeriodIsRequested() {
    var rma = request(RmaKind.RETURN, "PHONE", 2, List.of(IMEI_1, IMEI_2));

    assertThat(rma.status()).isEqualTo(RmaStatus.REQUESTED);
    assertThat(rma.productName()).isEqualTo("Galaxy S25");
    assertThat(rma.quantity()).isEqualTo(2);
    assertThat(rma.serialNumbers()).containsExactly(IMEI_1, IMEI_2);
    assertThat(rma.requestedAt()).isEqualTo(NOW);
    assertThat(rma.window()).isEqualTo(Window.returnPeriod(DELIVERED_AT, RETURN_PERIOD));
    assertThat(rma.decisions()).isEmpty();
    assertThat(rma.version()).isEqualTo(1);
  }

  @Test
  void aWarrantyClaimKeepsTheWarrantyWindowItWasCheckedAgainst() {
    var rma = request(RmaKind.WARRANTY_CLAIM, "PHONE", 1, List.of(IMEI_3));

    assertThat(rma.window())
        .isEqualTo(Window.warrantyWindow(DELIVERED_AT, new WarrantyTerms(24, "Samsung")));
  }

  @Test
  void aSerialNumberIsMatchedWhateverItsCase() {
    var rma = request(RmaKind.RETURN, "LAPTOP", 1, List.of(" sn-b2 "));

    assertThat(rma.serialNumbers()).containsExactly("SN-B2");
  }

  @Test
  void anUntrackedVariantTakesNoSerialNumbers() {
    assertThat(request(RmaKind.RETURN, "CASE", 4, List.of()).serialNumbers()).isEmpty();
    assertThatThrownBy(() -> request(RmaKind.RETURN, "CASE", 1, List.of("SN-X1")))
        .isInstanceOfSatisfying(
            SerialNumbersMiscountedException.class, e -> assertThat(e.expected()).isZero());
  }

  @Test
  void moreThanWasDeliveredIsRefused() {
    assertThatThrownBy(() -> request(RmaKind.RETURN, "CASE", 5, List.of()))
        .isInstanceOfSatisfying(
            QuantityExceededException.class, e -> assertThat(e.available()).isEqualTo(4));
  }

  @Test
  void aVariantNotDeliveredOnTheOrderHasNothingToClaim() {
    assertThatThrownBy(() -> request(RmaKind.RETURN, "TABLET", 1, List.of()))
        .isInstanceOfSatisfying(
            QuantityExceededException.class, e -> assertThat(e.available()).isZero());
  }

  @Test
  void otherRequestedAndApprovedRmasHoldTheirUnits() {
    others.add(request(RmaKind.RETURN, "CASE", 1, List.of()));
    others.add(request(RmaKind.RETURN, "CASE", 2, List.of()).approve("staff-7", null, NOW));

    assertThatThrownBy(() -> request(RmaKind.RETURN, "CASE", 2, List.of()))
        .isInstanceOfSatisfying(
            QuantityExceededException.class, e -> assertThat(e.available()).isEqualTo(1));
    assertThat(request(RmaKind.RETURN, "CASE", 1, List.of()).quantity()).isEqualTo(1);
  }

  @Test
  void aRejectedRmaHoldsNothing() {
    others.add(request(RmaKind.RETURN, "CASE", 4, List.of()).reject("staff-7", "Worn", NOW));
    others.add(
        request(RmaKind.RETURN, "PHONE", 1, List.of(IMEI_1)).reject("staff-7", "Scratched", NOW));

    assertThat(request(RmaKind.RETURN, "CASE", 4, List.of()).quantity()).isEqualTo(4);
    assertThat(request(RmaKind.RETURN, "PHONE", 1, List.of(IMEI_1)).serialNumbers())
        .containsExactly(IMEI_1);
  }

  @Test
  void anRmaForAnotherVariantHoldsNoneOfThisOnesUnits() {
    others.add(request(RmaKind.RETURN, "PHONE", 3, List.of(IMEI_1, IMEI_2, IMEI_3)));

    assertThat(request(RmaKind.RETURN, "CASE", 4, List.of()).quantity()).isEqualTo(4);
  }

  @Test
  void aSerialNumberInAnotherOpenOrApprovedRmaIsRefused() {
    others.add(request(RmaKind.RETURN, "PHONE", 1, List.of(IMEI_1)));
    others.add(
        request(RmaKind.WARRANTY_CLAIM, "PHONE", 1, List.of(IMEI_2)).approve("staff-7", null, NOW));

    assertThatThrownBy(() -> request(RmaKind.WARRANTY_CLAIM, "PHONE", 1, List.of(IMEI_1)))
        .isInstanceOfSatisfying(
            SerialNumberAlreadyClaimedException.class,
            e -> assertThat(e.serialNumber()).isEqualTo(IMEI_1));
    assertThatThrownBy(() -> request(RmaKind.RETURN, "PHONE", 1, List.of(IMEI_2)))
        .isInstanceOfSatisfying(
            SerialNumberAlreadyClaimedException.class,
            e -> assertThat(e.serialNumber()).isEqualTo(IMEI_2));
  }

  @Test
  void aSerialNumberNotShippedForThatVariantIsRefused() {
    assertThatThrownBy(() -> request(RmaKind.RETURN, "PHONE", 1, List.of("356938035643817")))
        .isInstanceOfSatisfying(
            SerialNumberNotShippedException.class,
            e -> assertThat(e.serialNumber()).isEqualTo("356938035643817"));
    // A laptop's serial number doesn't name a phone.
    assertThatThrownBy(() -> request(RmaKind.RETURN, "PHONE", 1, List.of("SN-A1")))
        .isInstanceOfSatisfying(
            SerialNumberNotShippedException.class,
            e -> assertThat(e.serialNumber()).isEqualTo("SN-A1"));
  }

  @Test
  void aSerialNumberNamedTwiceCountsOnce() {
    assertThatThrownBy(() -> request(RmaKind.RETURN, "PHONE", 2, List.of(IMEI_1, IMEI_1)))
        .isInstanceOfSatisfying(
            SerialNumberAlreadyClaimedException.class,
            e -> assertThat(e.serialNumber()).isEqualTo(IMEI_1));
  }

  @Test
  void theSerialNumbersMustNumberTheUnits() {
    assertThatThrownBy(() -> request(RmaKind.RETURN, "PHONE", 2, List.of(IMEI_1)))
        .isInstanceOfSatisfying(
            SerialNumbersMiscountedException.class, e -> assertThat(e.expected()).isEqualTo(2));
    assertThatThrownBy(() -> request(RmaKind.RETURN, "PHONE", 1, List.of()))
        .isInstanceOf(SerialNumbersMiscountedException.class);
  }

  @Test
  void aReturnAfterTheReturnPeriodIsRefusedWithWhenItEnded() {
    var lastMoment = DELIVERED_AT.plus(RETURN_PERIOD).minusNanos(1000);
    assertThat(requestAt(lastMoment, RmaKind.RETURN, "CASE", 1, TWO_YEARS).quantity()).isEqualTo(1);

    assertThatThrownBy(
            () -> requestAt(DELIVERED_AT.plus(RETURN_PERIOD), RmaKind.RETURN, "CASE", 1, TWO_YEARS))
        .isInstanceOfSatisfying(
            OutsideWindowException.class,
            e -> {
              assertThat(e.window().kind()).isEqualTo(Window.Kind.RETURN_PERIOD);
              assertThat(e.window().endsAt()).isEqualTo("2026-03-15T10:00:00Z");
            });
  }

  @Test
  void aWarrantyClaimAfterTheWarrantyWindowIsRefusedWithWhenItEnded() {
    var oneYear = Optional.of(new WarrantyTerms(12, "Samsung"));

    assertThatThrownBy(
            () ->
                requestAt(
                    Instant.parse("2027-03-01T10:00:00Z"),
                    RmaKind.WARRANTY_CLAIM,
                    "CASE",
                    1,
                    oneYear))
        .isInstanceOfSatisfying(
            OutsideWindowException.class,
            e -> {
              assertThat(e.window().kind()).isEqualTo(Window.Kind.WARRANTY_WINDOW);
              assertThat(e.window().endsAt()).isEqualTo("2027-03-01T10:00:00Z");
            });
    // A warranty claim after the Return Period, within the Warranty Window, is fine.
    assertThat(
            requestAt(
                    Instant.parse("2027-02-28T10:00:00Z"),
                    RmaKind.WARRANTY_CLAIM,
                    "CASE",
                    1,
                    oneYear)
                .kind())
        .isEqualTo(RmaKind.WARRANTY_CLAIM);
  }

  @Test
  void aWarrantyClaimOnAProductWithoutWarrantyTermsIsRefused() {
    assertThatThrownBy(() -> requestAt(NOW, RmaKind.WARRANTY_CLAIM, "CASE", 1, Optional.empty()))
        .isInstanceOf(NoWarrantyException.class);
    // A return doesn't need them.
    assertThat(requestAt(NOW, RmaKind.RETURN, "CASE", 1, Optional.empty()).kind())
        .isEqualTo(RmaKind.RETURN);
  }

  private Rma request(RmaKind kind, String variantId, int quantity, List<String> serialNumbers) {
    return Rma.request(
        UUID.randomUUID(),
        new RmaRequest(
            "customer-42", "order-1", variantId, quantity, serialNumbers, kind, "Changed my mind"),
        DELIVERY,
        others,
        RETURN_PERIOD,
        TWO_YEARS,
        NOW);
  }

  private Rma requestAt(
      Instant now, RmaKind kind, String variantId, int quantity, Optional<WarrantyTerms> warranty) {
    return Rma.request(
        UUID.randomUUID(),
        new RmaRequest("customer-42", "order-1", variantId, quantity, List.of(), kind, "Broken"),
        DELIVERY,
        others,
        RETURN_PERIOD,
        warranty,
        now);
  }
}
