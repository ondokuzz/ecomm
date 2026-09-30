package com.ecomm.promotions;

import com.ecomm.promotions.application.CouponService;
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

  /** Serves every Promotions use case: managing Coupons and evaluating one at checkout. */
  @Bean
  CouponService couponService(CouponRepository coupons, TimeSource time) {
    return new CouponService(coupons, time);
  }
}
