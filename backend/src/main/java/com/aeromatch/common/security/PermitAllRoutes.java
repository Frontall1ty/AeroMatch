package com.aeromatch.common.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;

/** The complete, validated set of unauthenticated routes. The security chain permits exactly these and nothing else. */
public final class PermitAllRoutes {

    /** One public route: explicit method + concrete path pattern. */
    public record Route(HttpMethod method, String pattern) {
        public Route {
            if (method == null) {
                throw new IllegalStateException("Public route needs an explicit HTTP method: " + pattern);
            }
            if (pattern == null || pattern.isBlank() || !pattern.startsWith("/")
                    || pattern.equals("/") || pattern.equals("/**") || pattern.equals("/*")) {
                throw new IllegalStateException("Public route needs a specific path pattern, got: '" + pattern + "'");
            }
        }

        @Override
        public String toString() {
            return method.name() + " " + pattern;
        }
    }

    private final List<Route> routes;

    PermitAllRoutes(List<Route> routes) {
        this.routes = List.copyOf(routes);
    }

    public List<Route> routes() {
        return routes;
    }

    /** Human-readable form, e.g. "GET /actuator/health/**". */
    public Set<String> described() {
        return routes.stream().map(Route::toString).collect(Collectors.toSet());
    }
}
