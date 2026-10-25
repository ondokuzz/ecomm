package com.ecomm.orchestration.application.port.out;

import io.temporal.activity.ActivityInterface;

/**
 * Promotions' internal command. A session redeems its Coupon once however often it is asked, so it
 * takes no key.
 */
@ActivityInterface
public interface PromotionsActivities {

  void redeemCoupon(String customerId, String checkoutSessionId, String couponCode);
}
