package com.ecomm.orchestration;

import io.temporal.api.testservice.v1.LockTimeSkippingRequest;
import io.temporal.api.testservice.v1.UnlockTimeSkippingRequest;
import io.temporal.serviceclient.TestServiceStubs;
import io.temporal.serviceclient.TestServiceStubsOptions;

/**
 * Test-only: holds Temporal's test server's clock still. The server skips time whenever every
 * workflow is only waiting, so a Saga waiting on a timer for an event the test has yet to send
 * would time out at once; locking time skipping makes it wait in real time instead. Locks count:
 * each {@link #lock()} needs its {@link #unlock()}.
 *
 * <p>The server also holds a lock while an activity is scheduled or running, released when it
 * completes. One whose workflow is terminated before its completion arrives keeps that lock for
 * good, and the clock then never skips again, so a test ends its run only once no activity is
 * pending ({@code FulfillmentWorkflowTest}).
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
}
