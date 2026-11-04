package com.ecomm.supportassistant.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a lookup found.
 *
 * @param callId the call it answers
 * @param content what the model reads, as JSON values: the context's answer trimmed to what the
 *     tool promises, or what went wrong
 * @param records the records it returned, such as Orders, which a reply may link to
 */
public record ToolResult(
    String callId,
    String tool,
    Outcome outcome,
    Map<String, Object> content,
    List<Reference> records) {

  public enum Outcome {
    FOUND,
    /** The context answered that there is no such record of the Customer's. */
    NOT_FOUND,
    /** The context couldn't be asked now: it timed out, failed or couldn't be reached. */
    ERROR
  }

  public ToolResult {
    // In the order given, as JSON keeps it, and a JSON null may be a value.
    content = Collections.unmodifiableMap(new LinkedHashMap<>(content));
    records = List.copyOf(records);
  }
}
