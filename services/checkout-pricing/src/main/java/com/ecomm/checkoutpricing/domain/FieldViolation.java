package com.ecomm.checkoutpricing.domain;

/** What is wrong with one field the Customer entered, such as {@code postcode}. */
public record FieldViolation(String field, String message) {}
