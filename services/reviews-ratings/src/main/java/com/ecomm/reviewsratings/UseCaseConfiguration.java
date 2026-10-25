package com.ecomm.reviewsratings;

import com.ecomm.reviewsratings.application.CustomerReviewsService;
import com.ecomm.reviewsratings.application.ModerationService;
import com.ecomm.reviewsratings.application.ProjectionService;
import com.ecomm.reviewsratings.application.ReadReviewsService;
import com.ecomm.reviewsratings.application.port.in.CustomerReviewsUseCase;
import com.ecomm.reviewsratings.application.port.in.ModerateReviewsUseCase;
import com.ecomm.reviewsratings.application.port.in.ProjectionUseCase;
import com.ecomm.reviewsratings.application.port.in.ReadReviewsUseCase;
import com.ecomm.reviewsratings.application.port.out.OrderStore;
import com.ecomm.reviewsratings.application.port.out.ProductStore;
import com.ecomm.reviewsratings.application.port.out.ReviewStore;
import com.ecomm.reviewsratings.application.port.out.TimeSource;
import com.ecomm.reviewsratings.domain.ReviewScreen;
import java.util.List;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Wires the framework-free use cases as beans, so {@code application} carries no Spring. */
@Configuration
class UseCaseConfiguration {

  @Bean
  ProjectionUseCase projectionUseCase(OrderStore orders, ProductStore products) {
    return new ProjectionService(orders, products);
  }

  @Bean
  ReadReviewsUseCase readReviewsUseCase(ReviewStore reviews) {
    return new ReadReviewsService(reviews);
  }

  @Bean
  CustomerReviewsUseCase customerReviewsUseCase(
      ReviewStore reviews,
      OrderStore orders,
      ProductStore products,
      TimeSource time,
      ReviewScreen screen) {
    return new CustomerReviewsService(reviews, orders, products, time, screen);
  }

  @Bean
  ModerateReviewsUseCase moderateReviewsUseCase(ReviewStore reviews, TimeSource time) {
    return new ModerationService(reviews, time);
  }

  /**
   * The words and patterns of {@code ecomm.reviews.screen}, a YAML list; none screens nothing. One
   * that isn't a regular expression fails the start.
   */
  @Bean
  ReviewScreen reviewScreen(Environment environment) {
    return ReviewScreen.of(
        Binder.get(environment)
            .bind("ecomm.reviews.screen", Bindable.listOf(String.class))
            .orElse(List.of()));
  }
}
