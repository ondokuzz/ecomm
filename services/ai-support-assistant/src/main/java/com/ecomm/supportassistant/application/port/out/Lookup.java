package com.ecomm.supportassistant.application.port.out;

/** What asking a context came to. Neither a miss nor a failure is thrown: each is a tool result. */
public sealed interface Lookup<T> {

  record Found<T>(T value) implements Lookup<T> {}

  /** The context answered that the Customer has no such record. */
  record NotFound<T>() implements Lookup<T> {}

  /**
   * The context couldn't be asked now: a timeout, a 5xx, a refused connection, or an answer it
   * shouldn't have given.
   */
  record Failed<T>(String reason) implements Lookup<T> {}
}
