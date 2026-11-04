package com.aeromatch.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the 12 h absolute session lifetime (ADR-007) from a server-side timestamp
 * ({@link SessionSecurity#AUTHENTICATED_AT}); Spring Session itself only enforces the idle timeout.
 * Fails closed: an authenticated request whose session has no (valid) timestamp is rejected.
 * Session-id rotation keeps session attributes, so it never extends the deadline. Never relies on the cleanup job.
 * Runs after the SecurityContext is loaded and before authorization.
 */
final class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    /** Request attribute: the presented session had exceeded the absolute lifetime (entry point answers SESSION_EXPIRED). */
    static final String EXPIRED_ATTRIBUTE = "aeromatch.sessionExpired";

    private final Clock clock;
    private final Duration absoluteTimeout;

    AbsoluteSessionTimeoutFilter(Clock clock, Duration absoluteTimeout) {
        this.clock = clock;
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(SessionSecurity.AUTHENTICATED_AT);
        if (!(value instanceof Instant authenticatedAt)) {
            dropSession(session);
            chain.doFilter(request, response); // continues as anonymous; protected routes answer 401 UNAUTHENTICATED
            return;
        }
        if (clock.instant().isAfter(authenticatedAt.plus(absoluteTimeout))) {
            dropSession(session);
            request.setAttribute(EXPIRED_ATTRIBUTE, Boolean.TRUE);
            chain.doFilter(request, response); // anonymous: public routes still work, protected ones answer SESSION_EXPIRED
            return;
        }
        chain.doFilter(request, response);
    }

    private void dropSession(HttpSession session) {
        SecurityContextHolder.clearContext();
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalid) {
                // nothing left to invalidate
            }
        }
    }
}
