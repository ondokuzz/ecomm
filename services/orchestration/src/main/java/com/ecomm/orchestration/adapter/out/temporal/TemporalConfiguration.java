package com.ecomm.orchestration.adapter.out.temporal;

import io.temporal.client.WorkflowClientOptions;
import io.temporal.spring.boot.TemporalOptionsCustomizer;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The client the workers run on carries the Correlation ID from workflows to activities. */
@Configuration
class TemporalConfiguration {

  @Bean
  TemporalOptionsCustomizer<WorkflowClientOptions.Builder> correlationIdPropagation() {
    return options -> options.setContextPropagators(List.of(new CorrelationIdPropagator()));
  }
}
