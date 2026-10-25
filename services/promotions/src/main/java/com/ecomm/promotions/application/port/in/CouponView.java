package com.ecomm.promotions.application.port.in;

import com.ecomm.promotions.domain.Coupon;
import com.ecomm.promotions.domain.CouponState;
import com.ecomm.promotions.domain.CouponUsage;

/** A Coupon with its redemptions and live Redemption holds, and where it stands now. */
public record CouponView(Coupon coupon, CouponUsage usage, CouponState state) {}
