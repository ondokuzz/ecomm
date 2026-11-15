package com.ecomm.notifications.application.port.out;

/** Counts what came of each Abandoned Cart: a Cart reminder recorded, or why none was. */
public interface CartReminderCounter {

  /**
   * What came of an abandonment. A reminder is counted once per event, as a redelivered event
   * records nothing more; a skip is counted on every delivery of its event.
   */
  enum Outcome {
    /** A reminder recorded, which the dispatcher then sends. */
    SENT,
    /** The Customer hasn't turned cart reminders on. */
    SKIPPED_OPTED_OUT,
    /** The Cart changed since it was abandoned. */
    SKIPPED_CHANGED,
    /** The Cart is gone or empty, or nothing in it is sold any more. */
    SKIPPED_EMPTY
  }

  void count(Outcome outcome);
}
