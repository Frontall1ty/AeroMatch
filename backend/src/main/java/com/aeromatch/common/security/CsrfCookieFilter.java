package com.aeromatch.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security 6 loads the CSRF token lazily, so the XSRF-TOKEN cookie would only be written when something reads it.
 * This filter forces the deferred token to be resolved on every request, so the cookie is always issued when absent.
 * Must run after {@code CsrfFilter}.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        DeferredCsrfToken deferred = (DeferredCsrfToken) request.getAttribute(DeferredCsrfToken.class.getName());
        if (deferred != null) {
            deferred.get(); // generates and stores (Set-Cookie) when the request carried no token
        }
        chain.doFilter(request, response);
    }
}
