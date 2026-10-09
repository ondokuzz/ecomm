package com.ecomm.commons.idempotency;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Refuses an {@link IdempotentCommand} that requires a key and was sent none. It wraps the
 * controller method inside its authorization checks, so a caller the command refuses gets a 403
 * whether or not it sent a key.
 */
class IdempotencyKeyRequirement implements MethodInterceptor {

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    var command =
        AnnotatedElementUtils.findMergedAnnotation(invocation.getMethod(), IdempotentCommand.class);
    if (command != null && command.required() && keyIsMissing()) {
      throw IdempotencyKeyException.required();
    }
    return invocation.proceed();
  }

  private static boolean keyIsMissing() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request
        && request.getRequest().getHeader(IdempotencyFilter.KEY_HEADER) == null;
  }
}
