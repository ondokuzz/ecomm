package com.ecomm.gateway;

import com.ecomm.commons.security.ResourceServerSecurity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
class GatewayConfiguration {

  @Bean
  EdgeRoutes edgeRoutes(GatewayProperties properties) {
    return EdgeRoutes.from(properties);
  }

  @Bean
  RouterFunction<ServerResponse> serviceRoutes(EdgeRoutes routes) {
    return routes.routerFunction();
  }

  /**
   * Authentication at the edge: a routed request needs a valid token unless it is a public read.
   * Anything else is let through to the dispatcher, which has no route for it and answers 404, so
   * an internal endpoint looks the same with a token as without. Authorization stays with the
   * services.
   */
  @Bean
  SecurityFilterChain edgeSecurity(
      HttpSecurity http,
      EdgeRoutes routes,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
      throws Exception {
    return ResourceServerSecurity.configure(http, exceptionResolver)
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(routes.publicReads())
                    .permitAll()
                    .requestMatchers(routes.routed())
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .build();
  }

  /** Just after the Correlation ID filter, so the ID is in the MDC; outside security. */
  @Bean
  FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
    var registration = new FilterRegistrationBean<>(new AccessLogFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }
}
