package com.aeromatch.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Defence in depth on top of the CSRF token: unsafe requests whose Origin (or, if absent, Referer) is not in the
 * allowlist are rejected with 403 CSRF_INVALID. If neither header is present the request passes this filter
 * and the CSRF token check still applies.
 */
final class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> UNSAFE = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final Set<String> allowedOrigins;
    private final SecurityErrorWriter errors;

    OriginCheckFilter(List<String> allowedOrigins, SecurityErrorWriter errors) {
        this.allowedOrigins = Set.copyOf(allowedOrigins.stream().map(OriginCheckFilter::normalize).toList());
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (UNSAFE.contains(request.getMethod())) {
            String origin = request.getHeader("Origin");
            String candidate = null;
            boolean present = false;
            if (origin != null) {
                present = true;
                candidate = normalize(origin);
            } else {
                String referer = request.getHeader("Referer");
                if (StringUtils.hasText(referer)) {
                    present = true;
                    candidate = originOf(referer);
                }
            }
            if (present && (candidate == null || !allowedOrigins.contains(candidate))) {
                errors.write(response, HttpServletResponse.SC_FORBIDDEN, SecurityErrorWriter.CSRF_INVALID,
                        "Request origin is not allowed");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String originOf(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            return normalize(uri.getScheme() + "://" + uri.getHost() + port);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalize(String origin) {
        String o = origin.trim().toLowerCase(Locale.ROOT);
        return o.endsWith("/") ? o.substring(0, o.length() - 1) : o;
    }
}
