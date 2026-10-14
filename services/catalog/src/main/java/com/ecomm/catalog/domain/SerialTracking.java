package com.ecomm.catalog.domain;

/**
 * How each unit of a Product's Variants is told apart when it ships: not at all, by a serial
 * number, or by an IMEI. It applies to all of a Product's Variants.
 */
public enum SerialTracking {
  NONE,
  SERIAL,
  IMEI
}
