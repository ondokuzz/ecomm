package com.ecomm.catalog.domain;

/** A category that at least one Product belongs to, and how many Products are in it. */
public record CategorySummary(String category, long productCount) {}
