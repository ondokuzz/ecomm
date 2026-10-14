package com.ecomm.fulfillmentshipping.domain;

import static com.ecomm.fulfillmentshipping.domain.ShipmentStatus.AWAITING_PICK;
import static com.ecomm.fulfillmentshipping.domain.ShipmentStatus.DELIVERED;
import static com.ecomm.fulfillmentshipping.domain.ShipmentStatus.ON_HOLD;
import static com.ecomm.fulfillmentshipping.domain.ShipmentStatus.PACKED;
import static com.ecomm.fulfillmentshipping.domain.ShipmentStatus.PICKED;
import static com.ecomm.fulfillmentshipping.domain.ShipmentStatus.SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@code AWAITING_PICK → PICKED → PACKED → SHIPPED → DELIVERED}, and {@code PACKED → ON_HOLD}, and
 * nothing else.
 */
class ShipmentStatusTest {

  private static final Map<ShipmentStatus, Set<ShipmentStatus>> ALLOWED =
      Map.of(
          AWAITING_PICK, Set.of(PICKED),
          PICKED, Set.of(PACKED),
          PACKED, Set.of(SHIPPED, ON_HOLD),
          SHIPPED, Set.of(DELIVERED),
          DELIVERED, Set.of(),
          ON_HOLD, Set.of());

  @Test
  void eachStatusMovesOnlyToTheNextOnesOfTheStateMachine() {
    for (var from : ShipmentStatus.values()) {
      for (var to : ShipmentStatus.values()) {
        assertThat(from.canMoveTo(to))
            .as("%s → %s", from, to)
            .isEqualTo(ALLOWED.get(from).contains(to));
      }
    }
  }

  @Test
  void deliveredAndOnHoldAreFinal() {
    assertThat(EnumSet.allOf(ShipmentStatus.class))
        .filteredOn(ShipmentStatus::isFinal)
        .containsExactlyInAnyOrder(DELIVERED, ON_HOLD);
  }
}
