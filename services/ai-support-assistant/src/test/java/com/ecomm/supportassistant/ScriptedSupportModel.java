package com.ecomm.supportassistant;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Stands in for the provider: answers what a test scripts, in order, and keeps every request it was
 * handed.
 */
class ScriptedSupportModel implements SupportModel {

  private final Deque<Answer> script = new ArrayDeque<>();
  private final List<Request> requests = new ArrayList<>();

  synchronized void script(Answer... answers) {
    script.addAll(List.of(answers));
  }

  synchronized List<Request> requests() {
    return List.copyOf(requests);
  }

  synchronized void reset() {
    script.clear();
    requests.clear();
  }

  @Override
  public synchronized Answer answer(Request request) {
    requests.add(request);
    if (script.isEmpty()) {
      throw new IllegalStateException("Nothing scripted for this provider call");
    }
    return script.removeFirst();
  }
}
