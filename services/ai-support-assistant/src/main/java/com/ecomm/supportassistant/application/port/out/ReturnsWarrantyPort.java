package com.ecomm.supportassistant.application.port.out;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.List;

/**
 * Returns & Warranty's reads of the Customer's own RMAs and of what their Orders are still eligible
 * for, made with their own token (ADR 0002).
 */
public interface ReturnsWarrantyPort {

  /**
   * The Customer's RMAs, newest first, at most {@code limit}: all of them, or those with the RMA
   * reference, or of the Order, or both, when given.
   *
   * @param reference its eight hex digits, without {@code RMA-}, or null for any
   * @param orderId null for any
   */
  Lookup<List<RmaView>> rmas(String reference, String orderId, int limit);

  /** One of the Customer's RMAs; not found for another Customer's. */
  Lookup<RmaView> rma(String rmaId);

  /**
   * What each delivered line of the Customer's Order is still eligible for; not found while the
   * Order isn't delivered, and for another Customer's.
   */
  Lookup<List<Eligibility>> eligibility(String orderId);

  /**
   * An RMA.
   *
   * @param kind {@code RETURN} or {@code WARRANTY_CLAIM}
   * @param windowEndsAt when the Return Period or Warranty Window it was checked against ends
   * @param refundAmount null for an RMA that refunds nothing
   * @param sendBackBy null, as is {@code warehouseId}, until it is approved
   * @param inspection null until its units are inspected
   * @param refundReference the reference of its Refund, once refunded; null otherwise
   * @param history oldest first
   */
  record RmaView(
      String id,
      String reference,
      String orderId,
      String productName,
      int quantity,
      String kind,
      String reason,
      Instant windowEndsAt,
      Money refundAmount,
      String status,
      Instant requestedAt,
      Instant sendBackBy,
      String warehouseId,
      Inspection inspection,
      String refundReference,
      List<Step> history) {}

  /**
   * @param note null unless a refusing outcome gave one
   */
  record Inspection(String outcome, String note) {}

  /**
   * A step in an RMA's history.
   *
   * @param note why, for a rejection, a hold or a closing; null when none was given
   */
  record Step(String status, Instant at, String note) {}

  /**
   * One delivered line of an Order.
   *
   * @param warrantyWindowEndsAt null when its Product has no Warranty terms, or isn't known yet
   * @param unitsFree how many of its units aren't held by an RMA, and so can still be claimed
   */
  record Eligibility(
      String variantId,
      String productName,
      Instant returnPeriodEndsAt,
      Instant warrantyWindowEndsAt,
      int unitsFree) {}
}
