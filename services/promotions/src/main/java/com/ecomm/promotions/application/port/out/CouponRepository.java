package com.ecomm.promotions.application.port.out;

import com.ecomm.promotions.domain.Coupon;
import java.util.List;
import java.util.Optional;

/** Where Coupons live, each under its upper-case code. */
public interface CouponRepository {

  /** Adds {@code coupon}; false, changing nothing, when its code is already taken. */
  boolean add(Coupon coupon);

  /** Replaces the Coupon with {@code coupon}'s code; false when there is none. */
  boolean replace(Coupon coupon);

  /** Removes the Coupon with this code; false when there is none. */
  boolean remove(String code);

  Optional<Coupon> find(String code);

  /** Every Coupon, by code. */
  List<Coupon> all();
}
