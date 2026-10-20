package com.ecomm.returnswarranty.domain;

/** An amount in a Currency's Minor unit, such as cents, and the Currency's ISO 4217 code. */
public record Money(long amountMinor, String currency) {}
