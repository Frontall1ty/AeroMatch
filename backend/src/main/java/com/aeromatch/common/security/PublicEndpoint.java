package com.aeromatch.common.security;

import org.springframework.http.HttpMethod;

/**
 * Extension point for routes that must be reachable without authentication (for example TB-004's
 * {@code POST /api/v1/auth/login}). Each endpoint names ONE explicit HTTP method and a non-empty path pattern;
 * method-less or match-everything routes are rejected at startup. Everything not listed is denied.
 * CSRF, Origin check and all other protections still apply to these routes. Every implementation is a
 * deliberate hole in deny-by-default and needs security review ({@code grep PublicEndpoint}).
 */
public interface PublicEndpoint {
    HttpMethod method();

    /** Path pattern, e.g. {@code /api/v1/auth/login}. */
    String pattern();
}
