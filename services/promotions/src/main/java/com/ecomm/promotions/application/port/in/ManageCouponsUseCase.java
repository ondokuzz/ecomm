package com.ecomm.promotions.application.port.in;

import com.ecomm.promotions.domain.Coupon;
import com.ecomm.promotions.domain.CouponAlreadyExistsException;
import com.ecomm.promotions.domain.CouponNotFoundException;
import java.util.List;
import java.util.Optional;

/**
 * Staff manage Coupons, and read each with its redemptions, live Redemption holds and state now. A
 * code is matched whatever its case. Changing a Coupon's Usage limits never takes back a hold or a
 * redemption already made.
 */
public interface ManageCouponsUseCase {

  /**
   * @throws CouponAlreadyExistsException when a Coupon has its code
   */
  CouponView create(Coupon coupon);

  /**
   * Replaces the Coupon with {@code coupon}'s code, every field of it.
   *
   * @throws CouponNotFoundException when there is none
   */
  CouponView update(Coupon coupon);

  /**
   * @throws CouponNotFoundException when there is none
   */
  void delete(String code);

  Optional<CouponView> coupon(String code);

  /** Every Coupon, by code. */
  List<CouponView> coupons();
}
