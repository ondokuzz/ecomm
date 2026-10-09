package com.ecomm.orchestration.adapter.out.temporal;

import com.ecomm.commons.web.CorrelationId;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.GlobalDataConverter;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Carries the Correlation ID from a workflow to its activities. The workflow puts the one it was
 * started with in its MDC; scheduling an activity copies it into the activity's header, and the
 * activity's thread puts it back in the MDC, where every log line and outbound call picks it up
 * ({@code CorrelationIdInterceptor}). An activity scheduled without one clears whatever the thread
 * last held.
 */
class CorrelationIdPropagator implements ContextPropagator {

  @Override
  public String getName() {
    return "correlationId";
  }

  @Override
  public Object getCurrentContext() {
    return CorrelationId.current().orElse(null);
  }

  @Override
  public void setCurrentContext(Object context) {
    if (context instanceof String id) {
      MDC.put(CorrelationId.MDC_KEY, id);
    } else {
      MDC.remove(CorrelationId.MDC_KEY);
    }
  }

  @Override
  public Map<String, Payload> serializeContext(Object context) {
    if (!(context instanceof String id)) {
      return Map.of();
    }
    return Map.of(CorrelationId.MDC_KEY, GlobalDataConverter.get().toPayload(id).orElseThrow());
  }

  @Override
  public Object deserializeContext(Map<String, Payload> header) {
    var payload = header.get(CorrelationId.MDC_KEY);
    return payload == null
        ? null
        : GlobalDataConverter.get().fromPayload(payload, String.class, String.class);
  }
}
