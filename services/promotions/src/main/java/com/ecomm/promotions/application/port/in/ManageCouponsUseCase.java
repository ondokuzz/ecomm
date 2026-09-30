package com.ecomm.promotions.application.port.in;

import com.ecomm.promotions.domain.Coupon;
import com.ecomm.promotions.domain.CouponAlreadyExistsException;
import com.ecomm.promotions.domain.CouponNotFoundException;
import java.util.List;
import java.util.Optional;

/** Staff manage Coupons. A code is matched whatever its case. */
public interface ManageCouponsUseCase {

  /**
   * @throws CouponAlreadyExistsException when a Coupon has its code
   */
  Coupon create(Coupon coupon);

  /**
   * Replaces the Coupon with {@code coupon}'s code, every field of it.
   *
   * @throws CouponNotFoundException when there is none
   */
  Coupon update(Coupon coupon);

  /**
   * @throws CouponNotFoundException when there is none
   */
  void delete(String code);

  Optional<Coupon> coupon(String code);

  /** Every Coupon, by code. */
  List<Coupon> coupons();
}
