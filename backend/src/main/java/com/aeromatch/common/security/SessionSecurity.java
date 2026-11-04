package com.aeromatch.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.stereotype.Component;

/**
 * The only supported way to establish an authenticated session. The login flow (TB-004) must call
 * {@link #establishAuthenticatedSession}; it (1) rotates the session id (fixation protection),
 * (2) stores the SecurityContext, (3) records the authentication time used by the 12 h absolute timeout,
 * and (4) rotates the CSRF token.
 */
@Component
public class SessionSecurity {

    /** Session attribute holding the {@link java.time.Instant} of authentication. Never reset by session-id rotation. */
    public static final String AUTHENTICATED_AT = "aeromatch.authenticatedAt";

    private final Clock clock;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    SessionSecurity(Clock clock, SecurityContextRepository securityContextRepository,
                    CsrfTokenRepository csrfTokenRepository) {
        this.clock = clock;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /** Call after credentials were verified. Starts a new absolute-timeout window. */
    public void establishAuthenticatedSession(Authentication authentication, HttpServletRequest request,
                                              HttpServletResponse response) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            if (existing.getAttribute(AUTHENTICATED_AT) != null || holdsAuthenticatedPrincipal(existing)) {
                // A user is already signed in on this session: nothing of it may carry over to the new login.
                existing.invalidate();
            } else {
                request.changeSessionId(); // session fixation: never keep the pre-authentication id
            }
        }
        HttpSession session = request.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        session.setAttribute(AUTHENTICATED_AT, clock.instant());

        rotateCsrfToken(request, response);
    }

    private static boolean holdsAuthenticatedPrincipal(HttpSession session) {
        Object ctx = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (ctx instanceof SecurityContext sc) {
            Authentication a = sc.getAuthentication();
            return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
        }
        return false;
    }

    /** Rotate the session id (e.g. company switch, step-up). Does NOT reset the absolute-timeout timestamp. */
    public void rotateSessionId(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
    }

    private void rotateCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken fresh = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(fresh, request, response);
        request.setAttribute(DeferredCsrfToken.class.getName(), new DeferredCsrfToken() {
            @Override
            public CsrfToken get() {
                return fresh;
            }

            @Override
            public boolean isGenerated() {
                return true;
            }
        });
    }
}
