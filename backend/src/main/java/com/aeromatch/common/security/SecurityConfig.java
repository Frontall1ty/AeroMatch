package com.aeromatch.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Deny-by-default security for the API (ADR-007): every request needs authentication unless explicitly listed.
 *
 * <p>This is the catch-all chain (lowest precedence, matches every request so nothing can bypass it). A future
 * machine-client chain (e.g. {@code /api/v1/integration/**}) must be declared with a higher precedence
 * (lower {@code @Order} value) and its own {@code securityMatcher}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AeroMatchSecurityProperties.class)
class SecurityConfig {

    static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

    /**
     * TODO(TB-005): springdoc routes are public only while {@code aeromatch.security.api-docs-public=true}
     * (fail-closed default false; true only in the local profile). TB-005 makes OpenAPI/Swagger profile-dependent
     * (disabled in prod); remove this list and the property then. Specific paths only.
     */
    static final List<String> SPRINGDOC_PATHS = List.of(
            "/api/v1/openapi", "/api/v1/openapi/**",
            "/api/v1/swagger-ui.html", "/api/v1/swagger-ui/**",
            "/swagger-ui.html", "/swagger-ui/**", "/webjars/swagger-ui/**");

    private static RequestMatcher springdocMatcher() {
        PathPatternRequestMatcher.Builder b = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(SPRINGDOC_PATHS.stream().map(b::matcher).toArray(RequestMatcher[]::new));
    }

    /** The complete permitAll set: fixed routes + (flagged) springdoc + PublicEndpoint beans. Validated at startup. */
    @Bean
    PermitAllRoutes permitAllRoutes(AeroMatchSecurityProperties props, ObjectProvider<PublicEndpoint> publicEndpoints) {
        List<PermitAllRoutes.Route> routes = new ArrayList<>();
        routes.add(new PermitAllRoutes.Route(HttpMethod.GET, "/actuator/health/**"));
        routes.add(new PermitAllRoutes.Route(HttpMethod.GET, "/api/v1/auth/csrf"));
        if (props.apiDocsPublic()) {
            SPRINGDOC_PATHS.forEach(p -> routes.add(new PermitAllRoutes.Route(HttpMethod.GET, p))); // TODO(TB-005): remove
        }
        publicEndpoints.orderedStream().forEach(e -> routes.add(new PermitAllRoutes.Route(e.method(), e.pattern())));
        return new PermitAllRoutes(routes);
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(AeroMatchSecurityProperties props) {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse(); // JS must read it
        repo.setCookieName(props.csrfCookieName());
        repo.setHeaderName(CSRF_HEADER_NAME);
        repo.setCookiePath("/");
        boolean secure = props.cookieSecure();
        repo.setCookieCustomizer(c -> c.secure(secure).sameSite("Lax").path("/"));
        return repo;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, AeroMatchSecurityProperties props,
                                               CsrfTokenRepository csrfTokenRepository,
                                               SecurityContextRepository securityContextRepository,
                                               ObjectMapper objectMapper, Clock clock,
                                               PermitAllRoutes permitAllRoutes) throws Exception {
        SecurityErrorWriter errors = new SecurityErrorWriter(objectMapper);
        RequestMatcher springdoc = springdocMatcher();

        http
                .securityMatcher(AnyRequestMatcher.INSTANCE)
                .authorizeHttpRequests(auth -> {
                    PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
                    permitAllRoutes.routes().forEach(r ->
                            auth.requestMatchers(paths.matcher(r.method(), r.pattern())).permitAll());
                    auth.anyRequest().authenticated();
                })
                .securityContext(c -> c.securityContextRepository(securityContextRepository))
                .requestCache(c -> c.requestCache(new NullRequestCache())) // never create sessions for anonymous requests
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .logout(l -> l
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, "/api/v1/auth/logout"))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(props.session().cookieName(), props.csrfCookieName())
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            if (req.getAttribute(AbsoluteSessionTimeoutFilter.EXPIRED_ATTRIBUTE) != null) {
                                errors.write(res, 401, SecurityErrorWriter.SESSION_EXPIRED,
                                        "Session expired. Please sign in again.");
                            } else {
                                errors.write(res, 401, SecurityErrorWriter.UNAUTHENTICATED, "Authentication required");
                            }
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            if (ex instanceof CsrfException) {
                                errors.write(res, 403, SecurityErrorWriter.CSRF_INVALID, "Invalid or missing CSRF token");
                            } else {
                                errors.write(res, 403, SecurityErrorWriter.FORBIDDEN, "Access denied");
                            }
                        }))
                .headers(h -> h
                        .contentTypeOptions(c -> { })
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                        // HSTS is written by Spring Security only on secure requests.
                        .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        // Cache-Control: no-cache, no-store, max-age=0 comes from the default cache-control writer.
                        // TODO(TB-005): drop the Swagger exception when springdoc is profile-dependent.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(springdoc),
                                new StaticHeadersWriter("Content-Security-Policy", API_CSP))))
                .addFilterBefore(new OriginCheckFilter(props.allowedOrigins(), errors), CsrfFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterAfter(new AbsoluteSessionTimeoutFilter(clock, props.session().absoluteTimeout()),
                        LogoutFilter.class);
        return http.build();
    }
}
