package com.ecomm.commons.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves a {@link CurrentCustomer} controller parameter from the JWT's {@code sub}. */
class CurrentCustomerArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.getParameterType() == CurrentCustomer.class;
  }

  @Override
  public CurrentCustomer resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof JwtAuthenticationToken token) {
      return new CurrentCustomer(token.getToken().getSubject());
    }
    throw new AuthenticationCredentialsNotFoundException("No bearer token for the Customer.");
  }
}
