package com.ecomm.returnswarranty.domain;

/**
 * A Product's manufacturer's warranty, as Catalog gives it: how many months it lasts from delivery,
 * and which manufacturer gives it.
 */
public record WarrantyTerms(int months, String manufacturer) {}
