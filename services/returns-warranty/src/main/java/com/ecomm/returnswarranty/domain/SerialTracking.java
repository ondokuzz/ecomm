package com.ecomm.returnswarranty.domain;

/**
 * How each unit of a Product's Variants is told apart when it ships, as Catalog says: not at all,
 * by a serial number, or by an IMEI. An RMA for a serial-tracked Variant names its units' Serial
 * numbers.
 */
public enum SerialTracking {
  NONE,
  SERIAL,
  IMEI;

  /** Whether a unit of a Variant tracked this way has a Serial number. */
  public boolean tracked() {
    return this != NONE;
  }

  /** Reads Catalog's value; a missing one, as in events from before Serial tracking, is NONE. */
  public static SerialTracking of(String value) {
    return value == null ? NONE : valueOf(value);
  }
}
