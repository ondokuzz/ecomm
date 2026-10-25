package com.ecomm.promotions.application;

import com.ecomm.promotions.application.port.in.RedeemCouponsUseCase;
import com.ecomm.promotions.application.port.out.CouponRepository;
import com.ecomm.promotions.application.port.out.RedemptionRepository;
import com.ecomm.promotions.application.port.out.TimeSource;
import com.ecomm.promotions.application.port.out.Transactions;
import com.ecomm.promotions.domain.CouponNotApplicableException;
import com.ecomm.promotions.domain.CouponRedemption;
import com.ecomm.promotions.domain.CouponRejection;
import com.ecomm.promotions.domain.RedemptionHold;

public class RedemptionService implements RedeemCouponsUseCase {

  private final CouponRepository coupons;
  private final RedemptionRepository redemptions;
  private final Transactions transactions;
  private final TimeSource time;

  public RedemptionService(
      CouponRepository coupons,
      RedemptionRepository redemptions,
      Transactions transactions,
      TimeSource time) {
    this.coupons = coupons;
    this.redemptions = redemptions;
    this.transactions = transactions;
    this.time = time;
  }

  @Override
  public void hold(RedemptionHold hold) {
    transactions.inTransaction(
        () -> {
          // Locked, so two sessions can't both take its last use.
          var coupon =
              coupons
                  .lock(hold.couponCode())
                  .orElseThrow(
                      () ->
                          new CouponNotApplicableException(
                              hold.couponCode(), CouponRejection.UNKNOWN));
          var now = time.now();
          coupon.checkHold(
              redemptions.usage(coupon.code(), now, hold.sessionId()),
              redemptions.usageBy(coupon.code(), hold.customerId(), now, hold.sessionId()));
          redemptions.hold(hold);
          return null;
        });
  }

  @Override
  public void release(String sessionId) {
    redemptions.release(sessionId);
  }

  @Override
  public void redeem(CouponRedemption redemption) {
    // Recorded even for a Coupon deleted since, so a code made again counts it.
    transactions.inTransaction(
        () -> {
          redemptions.redeem(redemption, time.now());
          return null;
        });
  }
}
