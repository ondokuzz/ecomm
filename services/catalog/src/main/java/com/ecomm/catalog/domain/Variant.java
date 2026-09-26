package com.ecomm.catalog.domain;

import com.ecomm.commons.money.Money;

/** A purchasable version of a Product, identified by its Variant ID, at its Price. */
public record Variant(String id, Money price) {}
