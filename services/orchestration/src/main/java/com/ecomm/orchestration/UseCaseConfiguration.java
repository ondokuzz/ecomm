package com.ecomm.orchestration;

import com.ecomm.orchestration.application.FulfillmentService;
import com.ecomm.orchestration.application.ReturnsService;
import com.ecomm.orchestration.application.port.out.FulfillmentWorkflows;
import com.ecomm.orchestration.application.port.out.ReturnsWorkflows;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires use cases as beans, so the application layer stays free of Spring annotations. The Sagas
 * themselves are Temporal's to create, on the workers in {@code spring.temporal.workers}.
 */
@Configuration
class UseCaseConfiguration {

  /** Starts and wakes the fulfillment Saga from the events Orchestration consumes. */
  @Bean
  FulfillmentService fulfillmentService(
      FulfillmentWorkflows workflows,
      @Value("${ecomm.orchestration.delivery-deadline}") Duration deliveryDeadline,
      @Value("${ecomm.orchestration.packing-check}") Duration packingCheck) {
    return new FulfillmentService(workflows, deliveryDeadline, packingCheck);
  }

  /** Starts and wakes the returns Saga from RMA events and its replacement Shipment's. */
  @Bean
  ReturnsService returnsService(
      ReturnsWorkflows workflows,
      @Value("${ecomm.orchestration.inspection-check}") Duration inspectionCheck,
      @Value("${ecomm.orchestration.packing-check}") Duration packingCheck) {
    return new ReturnsService(workflows, inspectionCheck, packingCheck);
  }
}
