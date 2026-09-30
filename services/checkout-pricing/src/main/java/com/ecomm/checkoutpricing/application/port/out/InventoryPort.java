package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.CartLine;
import com.ecomm.checkoutpricing.domain.OutOfStockException;
import java.time.Instant;
import java.util.List;

/** Inventory's Reservations, which hold Stock for a Customer until they pay. */
public interface InventoryPort {

  /**
   * Holds the lines' quantities of Stock for the Customer until {@code expiresAt}, all or none;
   * returns the Reservation's ID.
   *
   * @throws OutOfStockException when some Variant doesn't have enough
   */
  String reserve(String customerId, List<CartLine> lines, Instant expiresAt);

  /** Takes the Reservation's Stock off on-hand for good. */
  void commit(String customerId, String reservationId);

  /** Gives the Reservation's Stock back; one that is gone or already settled needs nothing. */
  void release(String customerId, String reservationId);
}
