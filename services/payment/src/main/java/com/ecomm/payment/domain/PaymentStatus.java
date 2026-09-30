package com.ecomm.payment.domain;

/**
 * Where a Payment stands with the gateway: approved, or declined for good. Capture and refund come
 * later.
 */
public enum PaymentStatus {
  AUTHORIZED,
  DECLINED
}
