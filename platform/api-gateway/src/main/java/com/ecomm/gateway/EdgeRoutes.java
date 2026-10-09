package com.ecomm.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.removeResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import com.ecomm.commons.web.CorrelationId;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The gateway's route table, built from {@link GatewayProperties}: which requests reach which
 * service, and which of those need no token. Routing and edge security both read it, so they can't
 * disagree. A request it doesn't route, an internal endpoint or a service's actuator included,
 * never leaves the gateway.
 */
final class EdgeRoutes {

  private static final RequestMatcher NOTHING = request -> false;

  /** Every service's health and metrics: for Compose and Prometheus, never for browsers. */
  private static final String ACTUATOR = "/actuator/**";

  private final List<Route> routes;

  private EdgeRoutes(List<Route> routes) {
    this.routes = routes;
  }

  static EdgeRoutes from(GatewayProperties properties) {
    return new EdgeRoutes(
        properties.services().entrySet().stream()
            .map(service -> Route.of(service.getKey(), service.getValue()))
            .toList());
  }

  /** Requests forwarded to a service: under a service's prefix and not internal. */
  RequestMatcher routed() {
    return anyOf(routes.stream().map(Route::routed).toList());
  }

  /** Routed requests that need no token: public reads, and webhooks the service authenticates. */
  RequestMatcher tokenless() {
    return new AndRequestMatcher(routed(), anyOf(routes.stream().map(Route::tokenless).toList()));
  }

  /**
   * Forwards each routed request, prefix stripped. The gateway forwards through a {@code
   * RestClient} built from Boot's builder, so {@code service-commons} sets the accepted Correlation
   * ID on the forwarded request, replacing the caller's, and logs the call.
   */
  RouterFunction<ServerResponse> routerFunction() {
    return routes.stream()
        .map(Route::routerFunction)
        .reduce(RouterFunction::and)
        .orElseThrow(() -> new IllegalStateException("No services under ecomm.gateway.services"));
  }

  private record Route(
      String name,
      GatewayProperties.Service service,
      RequestMatcher routed,
      RequestMatcher tokenless) {

    static Route of(String name, GatewayProperties.Service service) {
      Objects.requireNonNull(service.uri(), "ecomm.gateway.services." + name + ".uri");
      var prefix = "/api/" + name;
      var internal =
          anyOf(
              Stream.concat(Stream.of(ACTUATOR), service.internal().stream())
                  .map(entry -> endpoint(prefix, entry))
                  .toList());
      var routed =
          new AndRequestMatcher(path(null, prefix + "/**"), new NegatedRequestMatcher(internal));
      var tokenless =
          anyOf(
              Stream.concat(
                      service.publicReads().stream().map(p -> path(HttpMethod.GET, prefix + p)),
                      service.webhooks().stream().map(entry -> endpoint(prefix, entry)))
                  .toList());
      return new Route(name, service, routed, tokenless);
    }

    /**
     * {@code /path} for every method, or {@code METHOD /path}. It matches with a trailing slash
     * too, so {@code /stock/decrement/} can't slip through to a service that is lenient about it.
     */
    private static RequestMatcher endpoint(String prefix, String entry) {
      var parts = entry.trim().split("\\s+", 2);
      var method = parts.length == 1 ? null : HttpMethod.valueOf(parts[0]);
      var pattern = prefix + parts[parts.length - 1];
      return pattern.endsWith("/**")
          ? path(method, pattern)
          : new OrRequestMatcher(path(method, pattern), path(method, pattern + "/"));
    }

    RouterFunction<ServerResponse> routerFunction() {
      return route(name)
          .route(request -> routed.matches(request.servletRequest()), http())
          .before(uri(service.uri()))
          .before(stripPrefix(2))
          // The gateway's own filter has already set it; don't let the service's echo repeat it.
          .after(removeResponseHeader(CorrelationId.HEADER))
          .build();
    }
  }

  private static RequestMatcher path(HttpMethod method, String pattern) {
    return PathPatternRequestMatcher.withDefaults().matcher(method, pattern);
  }

  private static RequestMatcher anyOf(List<RequestMatcher> matchers) {
    return matchers.isEmpty() ? NOTHING : new OrRequestMatcher(matchers);
  }
}
