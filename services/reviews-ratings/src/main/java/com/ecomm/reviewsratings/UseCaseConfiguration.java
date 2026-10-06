package com.ecomm.reviewsratings;

import com.ecomm.reviewsratings.application.CustomerReviewsService;
import com.ecomm.reviewsratings.application.ProjectionService;
import com.ecomm.reviewsratings.application.ReadReviewsService;
import com.ecomm.reviewsratings.application.port.in.CustomerReviewsUseCase;
import com.ecomm.reviewsratings.application.port.in.ProjectionUseCase;
import com.ecomm.reviewsratings.application.port.in.ReadReviewsUseCase;
import com.ecomm.reviewsratings.application.port.out.OrderStore;
import com.ecomm.reviewsratings.application.port.out.ProductStore;
import com.ecomm.reviewsratings.application.port.out.ReviewStore;
import com.ecomm.reviewsratings.application.port.out.TimeSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
      ReviewStore reviews, OrderStore orders, ProductStore products, TimeSource time) {
    return new CustomerReviewsService(reviews, orders, products, time);
  }
}
