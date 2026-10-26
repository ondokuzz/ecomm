package com.ecomm.recommendations.domain;

/** A Product's Category, by its slug, with the name the Storefront shows. */
public record Category(String slug, String name) {}
