package com.ecomm.notifications.adapter.in.web;

import com.ecomm.commons.security.ResourceServerSecurity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * service-commons' rules, plus a Cart reminder's unsubscribe link with no token: the Customer opens
 * it from their email, or their mail client posts to it, without signing in, and the link's signed
 * token authenticates it instead.
 */
@Configuration(proxyBeanMethods = false)
class UnsubscribeSecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
      throws Exception {
    return ResourceServerSecurity.configure(http, exceptionResolver)
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/unsubscribe/cart-reminders")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .build();
  }
}
