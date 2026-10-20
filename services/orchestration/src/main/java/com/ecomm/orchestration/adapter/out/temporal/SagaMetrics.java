package com.ecomm.orchestration.adapter.out.temporal;

import com.ecomm.orchestration.application.port.in.CheckoutOutcome;
import com.ecomm.orchestration.application.port.in.FulfillmentOutcome;
import com.ecomm.orchestration.application.port.in.FulfillmentWorkflow;
import com.ecomm.orchestration.application.port.in.ReturnsOutcome;
import com.ecomm.orchestration.application.port.in.ReturnsWorkflow;
import com.ecomm.orchestration.application.port.out.CaptureActivities;
import com.ecomm.orchestration.application.port.out.CartActivities;
import com.ecomm.orchestration.application.port.out.CheckoutSessionActivities;
import com.ecomm.orchestration.application.port.out.InventoryActivities;
import com.ecomm.orchestration.application.port.out.OrderActivities;
import com.ecomm.orchestration.application.port.out.OrderFulfillmentActivities;
import com.ecomm.orchestration.application.port.out.OrderReturnActivities;
import com.ecomm.orchestration.application.port.out.PaymentActivities;
import com.ecomm.orchestration.application.port.out.RefundActivities;
import com.ecomm.orchestration.application.port.out.ReplacementActivities;
import com.ecomm.orchestration.application.port.out.ReturnsInventoryActivities;
import com.ecomm.orchestration.application.port.out.RmaActivities;
import com.ecomm.orchestration.application.port.out.ShipmentActivities;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.temporal.common.metadata.POJOActivityInterfaceMetadata;
import io.temporal.serviceclient.MetricsTag;
import io.temporal.worker.WorkerMetricsTag.WorkerType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reports each Saga's counters at 0 from the start: every outcome, compensation, hold and step.
 * Temporal sends a counter to Micrometer only once it has counted something, so without these
 * Prometheus would first see each series at 1, and Grafana's {@code increase()} would miss that
 * first count: the first void, say, after Orchestration starts.
 *
 * <p>Each is registered with the name and tags Temporal's scopes give it, so it is the very counter
 * the Saga's scope later increments: Temporal's default tags, the workflow's (or, for a step, its
 * activity's) and the counter's own. The steps are read from each Saga's activity interfaces, so a
 * new activity is reported at 0 too; a new compensation or kind of hold must be added below.
 */
@Component
class SagaMetrics {

  /**
   * A Saga's counters: {@code <workflowType>_<name>} with the tag {@code tag}, at each of {@code
   * values}.
   */
  record Counter(String name, String tag, List<String> values) {}

  /**
   * A Saga: its task queue and workflow type, its own counters, and its activities, each method a
   * step that counts its failures in {@code <workflowType>_step_failures}.
   */
  record Saga(
      String taskQueue, String workflowType, List<Counter> counters, List<Class<?>> activities) {}

  static final List<Saga> SAGAS =
      List.of(
          new Saga(
              "checkout",
              "checkout",
              List.of(
                  new Counter("workflows", "outcome", names(CheckoutOutcome.Status.values())),
                  new Counter("compensations", "kind", List.of("cancelOrder", "voidPayment"))),
              List.of(
                  OrderActivities.class,
                  PaymentActivities.class,
                  InventoryActivities.class,
                  CartActivities.class,
                  CheckoutSessionActivities.class)),
          new Saga(
              FulfillmentWorkflow.TASK_QUEUE,
              "fulfillment",
              List.of(
                  new Counter("workflows", "outcome", names(FulfillmentOutcome.Status.values())),
                  new Counter("holds", "kind", FulfillmentWorkflow.HOLDS)),
              List.of(
                  ShipmentActivities.class,
                  CaptureActivities.class,
                  OrderFulfillmentActivities.class)),
          new Saga(
              ReturnsWorkflow.TASK_QUEUE,
              "returns",
              List.of(
                  new Counter("workflows", "outcome", names(ReturnsOutcome.Status.values())),
                  new Counter("holds", "kind", ReturnsWorkflow.HOLDS)),
              List.of(
                  RmaActivities.class,
                  ReturnsInventoryActivities.class,
                  RefundActivities.class,
                  ReplacementActivities.class,
                  OrderReturnActivities.class)));

  private final MeterRegistry registry;
  private final String namespace;

  SagaMetrics(MeterRegistry registry, @Value("${spring.temporal.namespace}") String namespace) {
    this.registry = registry;
    this.namespace = namespace;
  }

  @EventListener(ApplicationStartedEvent.class)
  void reportEveryCounterAtZero() {
    for (var saga : SAGAS) {
      for (var counter : saga.counters()) {
        for (var value : counter.values()) {
          zero(saga.workflowType() + "_" + counter.name(), tagsOf(saga), counter.tag(), value);
        }
      }
      for (var activities : saga.activities()) {
        for (var step :
            POJOActivityInterfaceMetadata.newInstance(activities).getMethodsMetadata()) {
          var tags = tagsOf(saga);
          tags.put(MetricsTag.ACTIVITY_TYPE, step.getActivityTypeName());
          tags.put(MetricsTag.WORKER_TYPE, WorkerType.ACTIVITY_WORKER.getValue());
          zero(saga.workflowType() + "_step_failures", tags, "step", step.getMethod().getName());
        }
      }
    }
  }

  private Map<String, String> tagsOf(Saga saga) {
    var tags = new HashMap<>(MetricsTag.defaultTags(namespace));
    tags.put(MetricsTag.TASK_QUEUE, saga.taskQueue());
    tags.put(MetricsTag.WORKFLOW_TYPE, saga.workflowType());
    return tags;
  }

  private void zero(String name, Map<String, String> tags, String tag, String value) {
    var all = new HashMap<>(tags);
    all.put(tag, value);
    registry.counter(
        name, all.entrySet().stream().map(e -> Tag.of(e.getKey(), e.getValue())).toList());
  }

  private static List<String> names(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }
}
