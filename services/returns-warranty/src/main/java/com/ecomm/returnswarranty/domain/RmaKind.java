package com.ecomm.returnswarranty.domain;

/**
 * What a Customer asks for: to send back a Variant they don't want, within the Return Period, or a
 * warranty claim on a faulty one, within its Warranty Window.
 */
public enum RmaKind {
  RETURN,
  WARRANTY_CLAIM
}
