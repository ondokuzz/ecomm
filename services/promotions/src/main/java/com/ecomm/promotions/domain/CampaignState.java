package com.ecomm.promotions.domain;

import java.util.Locale;

/** Where a Campaign stands at a moment, as Staff see it in the list. */
public enum CampaignState {
  /** Active, and within its validity window: it applies. */
  RUNNING,
  /** Active, but its window hasn't started. */
  SCHEDULED,
  /** Active, but its window has ended. */
  OVER,
  /** Staff have switched it off, whatever its window. */
  OFF;

  /** How clients read it: {@code running}, {@code scheduled}, {@code over} or {@code off}. */
  public String label() {
    return name().toLowerCase(Locale.ROOT);
  }
}
