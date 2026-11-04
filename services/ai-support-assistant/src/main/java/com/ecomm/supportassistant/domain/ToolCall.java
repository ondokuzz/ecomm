package com.ecomm.supportassistant.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A lookup the model asked for: a tool by name, with its input as JSON values (maps, lists,
 * strings, numbers, booleans).
 *
 * @param id the model's ID for the call, which its result answers
 */
public record ToolCall(String id, String tool, Map<String, Object> input) {

  public ToolCall {
    // In the order given, as JSON keeps it, and a JSON null may be a value.
    input = Collections.unmodifiableMap(new LinkedHashMap<>(input));
  }
}
