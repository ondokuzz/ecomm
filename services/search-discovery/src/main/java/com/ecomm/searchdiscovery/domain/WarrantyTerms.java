package com.ecomm.searchdiscovery.domain;

/**
 * A Product's manufacturer's warranty, as Catalog published it: how many months it lasts, and which
 * manufacturer gives it. It applies to all of the Product's Variants.
 */
public record WarrantyTerms(int months, String manufacturer) {}
