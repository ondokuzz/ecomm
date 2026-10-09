package com.ecomm.commons.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a controller method's command takes an {@code Idempotency-Key} header. A repeat of
 * a request with the same key from the same caller gets the first response back, with {@code
 * Idempotent-Replayed: true}, and the command acts once (see {@link IdempotencyFilter}).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotentCommand {

  /** Whether a request without a key is refused with a 400, rather than acting without one. */
  boolean required() default true;
}
