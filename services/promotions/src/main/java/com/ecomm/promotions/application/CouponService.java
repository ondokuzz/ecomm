package com.ecomm.promotions.application;

import com.ecomm.promotions.application.port.in.CouponView;
import com.ecomm.promotions.application.port.in.ManageCouponsUseCase;
import com.ecomm.promotions.application.port.out.CouponRepository;
import com.ecomm.promotions.application.port.out.RedemptionRepository;
import com.ecomm.promotions.application.port.out.TimeSource;
import com.ecomm.promotions.domain.Coupon;
import com.ecomm.promotions.domain.CouponAlreadyExistsException;
import com.ecomm.promotions.domain.CouponNotFoundException;
import com.ecomm.promotions.domain.CouponUsage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class CouponService implements ManageCouponsUseCase {

  private final CouponRepository coupons;
  private final RedemptionRepository redemptions;
  private final TimeSource time;

  public CouponService(
      CouponRepository coupons, RedemptionRepository redemptions, TimeSource time) {
    this.coupons = coupons;
    this.redemptions = redemptions;
    this.time = time;
  }

  @Override
  public CouponView create(Coupon coupon) {
    if (!coupons.add(coupon)) {
      throw new CouponAlreadyExistsException(coupon.code());
    }
    return view(coupon);
  }

  @Override
  public CouponView update(Coupon coupon) {
    if (!coupons.replace(coupon)) {
      throw new CouponNotFoundException(coupon.code());
    }
    return view(coupon);
  }

  @Override
  public void delete(String code) {
    if (!coupons.remove(Coupon.normalize(code))) {
      throw new CouponNotFoundException(code);
    }
  }

  @Override
  public Optional<CouponView> coupon(String code) {
    return coupons.find(Coupon.normalize(code)).map(this::view);
  }

  @Override
  public List<CouponView> coupons() {
    var now = time.now();
    var usage = redemptions.usageOfEvery(now);
    return coupons.all().stream()
        .map(c -> view(c, usage.getOrDefault(c.code(), CouponUsage.NONE), now))
        .toList();
  }

  private CouponView view(Coupon coupon) {
    var now = time.now();
    return view(coupon, redemptions.usage(coupon.code(), now, null), now);
  }

  private static CouponView view(Coupon coupon, CouponUsage usage, Instant now) {
    return new CouponView(coupon, usage, coupon.stateAt(now, usage));
  }
}
