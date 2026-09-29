package com.ecomm.catalog.domain;

/** A Category and how many Products are in it. */
public record CategorySummary(Category category, long productCount) {}
