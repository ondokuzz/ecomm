package com.ecomm.recommendations.domain;

/** Why a Product is recommended with another. */
public enum Reason {
  /** Counting Orders bought both. */
  BOUGHT_TOGETHER,
  /** One of the other's Category's best sellers, filling up when too few were bought together. */
  POPULAR_IN_CATEGORY
}
