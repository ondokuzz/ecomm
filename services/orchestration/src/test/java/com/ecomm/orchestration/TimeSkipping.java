package com.ecomm.orchestration;

import com.google.protobuf.util.Durations;
import io.temporal.api.testservice.v1.LockTimeSkippingRequest;
import io.temporal.api.testservice.v1.SleepRequest;
import io.temporal.api.testservice.v1.UnlockTimeSkippingRequest;
import io.temporal.serviceclient.TestServiceStubs;
import io.temporal.serviceclient.TestServiceStubsOptions;
import java.time.Duration;

/**
 * Test-only: holds Temporal's test server's clock still, or moves it on. The server skips time
 * whenever every workflow is only waiting, so a Saga waiting on a timer for an event the test has
 * yet to send would time out at once; locking time skipping makes it wait in real time instead.
 * Locks count: each {@link #lock()} needs its {@link #unlock()}.
 */
final class TimeSkipping {

  private static final TestServiceStubs SERVER =
      TestServiceStubs.newServiceStubs(
          TestServiceStubsOptions.newBuilder()
              .setTarget("localhost:" + OrchestrationTest.TEMPORAL_PORT)
              .validateAndBuildWithDefaults());

  private TimeSkipping() {}

  static void lock() {
    SERVER.blockingStub().lockTimeSkipping(LockTimeSkippingRequest.getDefaultInstance());
  }

  static void unlock() {
    SERVER.blockingStub().unlockTimeSkipping(UnlockTimeSkippingRequest.getDefaultInstance());
  }

  /**
   * Skips {@code duration} on the server's clock, firing every timer due in it, then locks again.
   */
  static void skip(Duration duration) {
    SERVER
        .blockingStub()
        .unlockTimeSkippingWithSleep(
            SleepRequest.newBuilder()
                .setDuration(Durations.fromMillis(duration.toMillis()))
                .build());
  }
}
