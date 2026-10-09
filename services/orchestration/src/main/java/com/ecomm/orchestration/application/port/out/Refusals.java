package com.ecomm.orchestration.application.port.out;

/**
 * How an activity reports that a context refused its call with a 4xx. A refusal says the request
 * itself is wrong, so the steps that would only be refused again don't retry it.
 */
public final class Refusals {

  /** The failure type of a call another context refused. */
  public static final String REFUSED = "Refused";

  private Refusals() {}
}
