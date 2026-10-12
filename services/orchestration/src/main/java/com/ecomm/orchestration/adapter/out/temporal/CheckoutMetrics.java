package com.ecomm.orchestration.adapter.out.temporal;

import com.ecomm.orchestration.application.port.in.CheckoutOutcome;
import com.ecomm.orchestration.application.port.out.CartActivities;
import com.ecomm.orchestration.application.port.out.CheckoutSessionActivities;
import com.ecomm.orchestration.application.port.out.InventoryActivities;
import com.ecomm.orchestration.application.port.out.OrderActivities;
import com.ecomm.orchestration.application.port.out.PaymentActivities;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.temporal.common.metadata.POJOActivityInterfaceMetadata;
import io.temporal.serviceclient.MetricsTag;
import io.temporal.worker.WorkerMetricsTag.WorkerType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reports the checkout Saga's counters at 0 from the start, for every outcome, compensation and
 * step. Temporal sends a counter to Micrometer only once it has counted something, so without these
 * Prometheus would first see each series at 1, and Grafana's {@code increase()} would miss that
 * first count: the first void, say, after Orchestration starts.
 *
 * <p>Each is registered with the name and tags Temporal's scopes give it, so it is the very counter
 * the Saga's scope later increments: Temporal's default tags, the workflow's (or, for a step, its
 * activity's) and the counter's own. The steps are read from the Saga's activity interfaces, so a
 * new activity is reported at 0 too; a new compensation must be added to {@link #COMPENSATIONS}.
 */
@Component
class CheckoutMetrics {

  static final String TASK_QUEUE = "checkout";
  static final String WORKFLOW_TYPE = "checkout";

  /** Undone by the Saga when a later step can't go on. */
  static final List<String> COMPENSATIONS = List.of("cancelOrder", "voidPayment");

  /**
   * The Saga's activities. Each method is a step, which counts its failures under the method's
   * name, and Temporal tags them with its activity type.
   */
  static final List<Class<?>> ACTIVITIES =
      List.of(
          OrderActivities.class,
          PaymentActivities.class,
          InventoryActivities.class,
          CartActivities.class,
          CheckoutSessionActivities.class);

  private final MeterRegistry registry;
  private final String namespace;

  CheckoutMetrics(MeterRegistry registry, @Value("${spring.temporal.namespace}") String namespace) {
    this.registry = registry;
    this.namespace = namespace;
  }

  @EventListener(ApplicationStartedEvent.class)
  void reportEveryCounterAtZero() {
    for (var outcome : CheckoutOutcome.Status.values()) {
      zero("checkout_workflows", workflowTags(), "outcome", outcome.name());
    }
    for (var kind : COMPENSATIONS) {
      zero("checkout_compensations", workflowTags(), "kind", kind);
    }
    for (var activities : ACTIVITIES) {
      for (var step : POJOActivityInterfaceMetadata.newInstance(activities).getMethodsMetadata()) {
        var tags = workflowTags();
        tags.put(MetricsTag.ACTIVITY_TYPE, step.getActivityTypeName());
        tags.put(MetricsTag.WORKER_TYPE, WorkerType.ACTIVITY_WORKER.getValue());
        zero("checkout_step_failures", tags, "step", step.getMethod().getName());
      }
    }
  }

  private Map<String, String> workflowTags() {
    var tags = new HashMap<>(MetricsTag.defaultTags(namespace));
    tags.put(MetricsTag.TASK_QUEUE, TASK_QUEUE);
    tags.put(MetricsTag.WORKFLOW_TYPE, WORKFLOW_TYPE);
    return tags;
  }

  private void zero(String name, Map<String, String> tags, String tag, String value) {
    var all = new HashMap<>(tags);
    all.put(tag, value);
    registry.counter(
        name, all.entrySet().stream().map(e -> Tag.of(e.getKey(), e.getValue())).toList());
  }
}
