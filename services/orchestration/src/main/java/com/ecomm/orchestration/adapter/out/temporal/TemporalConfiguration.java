package com.ecomm.orchestration.adapter.out.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.spring.boot.TemporalOptionsCustomizer;
import io.temporal.worker.WorkflowImplementationOptions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The client the workers run on carries the Correlation ID from workflows to activities, and the
 * checkout Saga awaits a pending authorization's settlement for {@code
 * ecomm.orchestration.settlement-deadline}.
 */
@Configuration
class TemporalConfiguration {

  /** The activity type of {@code PaymentActivities.awaitSettlement}, as Temporal names it. */
  private static final String AWAIT_SETTLEMENT = "AwaitSettlement";

  @Bean
  TemporalOptionsCustomizer<WorkflowClientOptions.Builder> correlationIdPropagation() {
    return options -> options.setContextPropagators(List.of(new CorrelationIdPropagator()));
  }

  /**
   * Overrides the deadline the Saga's code gives the settlement read, keeping its backoff. Temporal
   * records each activity's timeouts when it schedules it, so a run replays the same whatever this
   * is set to later.
   */
  @Bean
  TemporalOptionsCustomizer<WorkflowImplementationOptions.Builder> settlementDeadline(
      @Value("${ecomm.orchestration.settlement-deadline}") Duration deadline) {
    return options ->
        options.setActivityOptions(
            Map.of(
                AWAIT_SETTLEMENT,
                ActivityOptions.newBuilder().setScheduleToCloseTimeout(deadline).build()));
  }
}
