package com.ecomm.supportassistant;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Stands in for the provider: answers what a test scripts, in order, and keeps every request it was
 * handed. A test can hold the next call open until it lets it answer.
 */
class ScriptedSupportModel implements SupportModel {

  private final Deque<Answer> script = new ArrayDeque<>();
  private final List<Request> requests = new ArrayList<>();
  private Hold hold;

  /** The next provider call, asked and held until {@link #release()}d. */
  static final class Hold {

    private final CountDownLatch asked = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    /** Waits until the turn has asked the provider. */
    void awaitAsked() throws InterruptedException {
      if (!asked.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("The provider was not asked");
      }
    }

    /** Lets the held call answer what is scripted. */
    void release() {
      released.countDown();
    }
  }

  synchronized void script(Answer... answers) {
    script.addAll(List.of(answers));
  }

  synchronized List<Request> requests() {
    return List.copyOf(requests);
  }

  /** Holds the next provider call open. */
  synchronized Hold holdNext() {
    hold = new Hold();
    return hold;
  }

  synchronized void reset() {
    script.clear();
    requests.clear();
    if (hold != null) {
      hold.release();
      hold = null;
    }
  }

  @Override
  public Answer answer(Request request) {
    Hold held;
    synchronized (this) {
      held = hold;
      hold = null;
    }
    if (held != null) {
      held.asked.countDown();
      try {
        held.released.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    synchronized (this) {
      requests.add(request);
      if (script.isEmpty()) {
        throw new IllegalStateException("Nothing scripted for this provider call");
      }
      return script.removeFirst();
    }
  }
}
