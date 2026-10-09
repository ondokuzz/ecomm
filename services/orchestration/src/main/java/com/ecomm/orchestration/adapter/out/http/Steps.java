package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.out.Refusals;
import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.client.HttpClientErrorException;

/** What every step's call shares: its idempotency key, and how its failures are reported. */
final class Steps {

  private Steps() {}

  /**
   * The {@code Idempotency-Key} of the step {@code step} in this run: {@code
   * <workflowId>:<runId>:<step>}. It stays the same across the step's retries, so a retry replays;
   * another run of the same workflow, paying the session again, gets keys of its own (ADR 0009).
   */
  static String idempotencyKey(String step) {
    var info = Activity.getExecutionContext().getInfo();
    return info.getWorkflowId() + ":" + info.getWorkflowRunId() + ":" + step;
  }

  /**
   * Runs the call of step {@code step}. A 4xx left after the client's one retry on a 401 fails it
   * as {@link Refusals#REFUSED}; anything else fails as it is, for Temporal to retry. Either way
   * the failure is counted under the step's name.
   *
   * <p>The failure Temporal keeps says what went wrong in its message alone, with no stack and no
   * cause: the HTTP client's stacks run past the server's size limit for an activity's failure, and
   * Temporal's UI would then show only "Failure exceeds size limit" in place of why a step was
   * retried.
   */
  static <T> T call(String step, Supplier<T> call) {
    try {
      return call.get();
    } catch (HttpClientErrorException e) {
      countFailure(step);
      throw compact(
          ApplicationFailure.newFailure(
              step
                  + " was refused: "
                  + e.getStatusCode().value()
                  + " "
                  + e.getResponseBodyAsString(),
              Refusals.REFUSED));
    } catch (RuntimeException e) {
      countFailure(step);
      throw compact(
          ApplicationFailure.newFailure(
              step + " failed: " + atMost500(e.getMessage()) + rootCause(e),
              e.getClass().getSimpleName()));
    }
  }

  /**
   * What lies under {@code e}, such as the host that couldn't be reached; empty when nothing does.
   */
  private static String rootCause(RuntimeException e) {
    var root = NestedExceptionUtils.getMostSpecificCause(e);
    return root == e ? "" : " (" + root + ")";
  }

  private static String atMost500(String text) {
    return text == null || text.length() <= 500 ? text : text.substring(0, 500) + "…";
  }

  private static ApplicationFailure compact(ApplicationFailure failure) {
    failure.setStackTrace(new StackTraceElement[0]);
    return failure;
  }

  static void run(String step, Runnable call) {
    call(
        step,
        () -> {
          call.run();
          return null;
        });
  }

  static void countFailure(String step) {
    Activity.getExecutionContext()
        .getMetricsScope()
        .tagged(Map.of("step", step))
        .counter("checkout_step_failures")
        .inc(1);
  }
}
