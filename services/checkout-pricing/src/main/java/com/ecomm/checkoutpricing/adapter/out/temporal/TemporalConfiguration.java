package com.ecomm.checkoutpricing.adapter.out.temporal;

import io.temporal.serviceclient.RpcRetryOptions;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.spring.boot.TemporalOptionsCustomizer;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Temporal's client retries a call it can't make for a minute by default. A Customer pressing Pay
 * shouldn't wait that long to hear that payments are down, so a call gives up after {@code
 * ecomm.checkout.saga.unavailable-after} (5 seconds by default) and paying is a 503.
 */
@Configuration
class TemporalConfiguration {

  @Bean
  TemporalOptionsCustomizer<WorkflowServiceStubsOptions.Builder> giveUpOnAnUnreachableTemporal(
      @Value("${ecomm.checkout.saga.unavailable-after:5s}") Duration unavailableAfter) {
    return options ->
        options.setRpcRetryOptions(
            RpcRetryOptions.newBuilder().setExpiration(unavailableAfter).build());
  }
}
