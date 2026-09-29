package com.ecomm.commons.web;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * The Correlation ID that names a request as it travels between services. It arrives and leaves in
 * the {@value #HEADER} header, and while a request is served it sits in the MDC under {@value
 * #MDC_KEY}, so every log line carries it.
 *
 * <p>It is not a security boundary: an incoming one is kept only if well-formed, and it is never
 * used for any decision.
 */
public final class CorrelationId {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9-]{1,64}");

  private CorrelationId() {}

  /** The Correlation ID of the request this thread is serving, if any. */
  public static Optional<String> current() {
    return Optional.ofNullable(MDC.get(MDC_KEY));
  }

  /** {@code incoming} if it is at most 64 characters of {@code [A-Za-z0-9-]}, else a new UUID. */
  static String acceptOrGenerate(String incoming) {
    return incoming != null && WELL_FORMED.matcher(incoming).matches()
        ? incoming
        : UUID.randomUUID().toString();
  }
}
