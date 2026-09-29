package com.ecomm.commons.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

/**
 * Problem details for every error, and a Correlation ID on every request and its problem details.
 * Every call made through a {@code RestClient} built from Boot's {@code RestClient.Builder} sends
 * the Correlation ID and is logged.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({ProblemDetailExceptionHandler.class, CorrelationIdProblemDetailAdvice.class})
public class CommonsWebAutoConfiguration {

  /** Runs first, ahead of the security filter chain, so 401s and 403s carry the ID too. */
  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
    var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(RestClientCustomizer.class)
  static class OutboundCorrelationId {

    @Bean
    RestClientCustomizer correlationIdRestClientCustomizer() {
      return builder ->
          builder
              .requestInterceptor(new OutboundCallLogInterceptor())
              .requestInterceptor(new CorrelationIdInterceptor());
    }
  }
}
