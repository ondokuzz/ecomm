package com.ecomm.promotions.application;

import com.ecomm.promotions.application.port.in.ManageCouponsUseCase;
import com.ecomm.promotions.application.port.out.CouponRepository;
import com.ecomm.promotions.application.port.out.TimeSource;
import com.ecomm.promotions.domain.Coupon;
import com.ecomm.promotions.domain.CouponAlreadyExistsException;
import com.ecomm.promotions.domain.CouponNotFoundException;
import java.util.List;
import java.util.Optional;

public class CouponService implements ManageCouponsUseCase {

  private final CouponRepository coupons;
  private final TimeSource time;

  public CouponService(CouponRepository coupons, TimeSource time) {
    this.coupons = coupons;
    this.time = time;
  }

  @Override
  public Coupon create(Coupon coupon) {
    if (!coupons.add(coupon)) {
      throw new CouponAlreadyExistsException(coupon.code());
    }
    return coupon;
  }

  @Override
  public Coupon update(Coupon coupon) {
    if (!coupons.replace(coupon)) {
      throw new CouponNotFoundException(coupon.code());
    }
    return coupon;
  }

  @Override
  public void delete(String code) {
    if (!coupons.remove(Coupon.normalize(code))) {
      throw new CouponNotFoundException(code);
    }
  }

  @Override
  public Optional<Coupon> coupon(String code) {
    return coupons.find(Coupon.normalize(code));
  }

  @Override
  public List<Coupon> coupons() {
    return coupons.all();
  }
}
