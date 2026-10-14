package com.ecomm.returnswarranty.domain;

/**
 * Where an RMA is: {@code REQUESTED} by the Customer, then {@code APPROVED} or {@code REJECTED} by
 * Staff's RMA decision, which is final this sprint. Sprint 6 continues from {@code APPROVED}.
 */
public enum RmaStatus {
  REQUESTED,
  APPROVED,
  REJECTED
}
