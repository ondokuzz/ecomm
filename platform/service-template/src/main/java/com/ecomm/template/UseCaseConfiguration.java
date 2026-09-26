package com.ecomm.template;

import com.ecomm.template.application.PingService;
import com.ecomm.template.application.port.in.PingUseCase;
import com.ecomm.template.application.port.out.TimeSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. Adapters are
 * ordinary Spring components.
 */
@Configuration
class UseCaseConfiguration {

  @Bean
  PingUseCase pingUseCase(
      @Value("${spring.application.name}") String serviceName, TimeSource timeSource) {
    return new PingService(serviceName, timeSource);
  }
}
