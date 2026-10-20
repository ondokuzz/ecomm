package com.ecomm.returnswarranty.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * What of a delivered line is still free to claim in an RMA: how many units, and which Serial
 * numbers for a serial-tracked Variant. Units held by an RMA aren't, until it is rejected, expires
 * or is closed.
 */
public record Availability(int quantity, List<String> serialNumbers) {

  public Availability {
    serialNumbers = List.copyOf(serialNumbers);
  }

  /**
   * What of {@code line} is free, given RMAs on its Order's Variant; those that don't hold units
   * are ignored.
   */
  public static Availability of(DeliveredLine line, Collection<Rma> rmas) {
    var holding = rmas.stream().filter(Rma::holdsUnits).toList();
    var claimed = new HashSet<String>();
    holding.forEach(rma -> claimed.addAll(rma.serialNumbers()));
    return new Availability(
        Math.max(line.quantity() - holding.stream().mapToInt(Rma::quantity).sum(), 0),
        line.serialNumbers().stream().filter(s -> !claimed.contains(s)).toList());
  }
}
