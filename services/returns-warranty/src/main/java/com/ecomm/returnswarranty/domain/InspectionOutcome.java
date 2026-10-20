package com.ecomm.returnswarranty.domain;

/**
 * What Staff found when they inspected an RMA's units: a return {@code ACCEPTED} or {@code
 * REFUSED}, or a warranty claim's fault {@code FAULT_CONFIRMED} or {@code NO_FAULT_FOUND}.
 */
public enum InspectionOutcome {
  ACCEPTED(RmaKind.RETURN, false),
  REFUSED(RmaKind.RETURN, true),
  FAULT_CONFIRMED(RmaKind.WARRANTY_CLAIM, false),
  NO_FAULT_FOUND(RmaKind.WARRANTY_CLAIM, true);

  private final RmaKind kind;
  private final boolean refusing;

  InspectionOutcome(RmaKind kind, boolean refusing) {
    this.kind = kind;
    this.refusing = refusing;
  }

  /** The kind of RMA it is an outcome for. */
  public RmaKind kind() {
    return kind;
  }

  /**
   * Whether it refuses the Customer what they asked for, so that it must be explained in a note.
   */
  public boolean refusing() {
    return refusing;
  }
}
