package com.ecomm.promotions;

import com.ecomm.promotions.application.CampaignService;
import com.ecomm.promotions.application.CouponService;
import com.ecomm.promotions.application.DiscountService;
import com.ecomm.promotions.application.port.out.CampaignRepository;
import com.ecomm.promotions.application.port.out.CatalogPort;
import com.ecomm.promotions.application.port.out.CouponRepository;
import com.ecomm.promotions.application.port.out.TimeSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  /** Serves managing Coupons. */
  @Bean
  CouponService couponService(CouponRepository coupons, TimeSource time) {
    return new CouponService(coupons, time);
  }

  /** Serves evaluating every Discount a Checkout Session is due. */
  @Bean
  DiscountService discountService(
      CampaignRepository campaigns, CouponRepository coupons, TimeSource time) {
    return new DiscountService(campaigns, coupons, time);
  }

  /** Serves managing Campaigns, checked against Catalog. */
  @Bean
  CampaignService campaignService(
      CampaignRepository campaigns, CatalogPort catalog, TimeSource time) {
    return new CampaignService(campaigns, catalog, time);
  }
}
