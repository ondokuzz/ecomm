package com.ecomm.commons.idempotency;

import java.util.Arrays;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.context.SecurityContextHolder;
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
    if (command != null && command.required() && keyIsMissing() && !callerIsExempt(command)) {
      throw IdempotencyKeyException.required();
    }
    return invocation.proceed();
  }

  private static boolean keyIsMissing() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request
        && request.getRequest().getHeader(IdempotencyFilter.KEY_HEADER) == null;
  }

  /** Whether the caller holds one of the roles the command exempts from its key. */
  private static boolean callerIsExempt(IdempotentCommand command) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && Arrays.stream(command.exemptRoles())
            .anyMatch(
                role ->
                    authentication.getAuthorities().stream()
                        .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority())));
  }
}
