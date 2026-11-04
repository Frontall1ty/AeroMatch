package com.aeromatch.testsupport;

import com.aeromatch.common.security.PublicEndpoint;
import com.aeromatch.common.security.SessionSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEST-ONLY endpoints. Lives under src/test and is only active when aeromatch.test-support.enabled=true,
 * so it can never be part of the production artifact. Real login arrives with TB-004.
 */
@RestController
@RequestMapping("/api/v1/test")
@ConditionalOnProperty("aeromatch.test-support.enabled")
class TestSupportController {

    static final String MARKER = "aeromatch.test.marker";

    /** The test login must be reachable anonymously; it goes through the real PublicEndpoint hook (CSRF still applies). */
    @Bean
    static PublicEndpoint testLoginIsPublic() {
        return new PublicEndpoint() {
            @Override
            public HttpMethod method() {
                return HttpMethod.POST;
            }

            @Override
            public String pattern() {
                return "/api/v1/test/login";
            }
        };
    }

    /** A public route whose handler is method-secured, to prove anonymous callers get 401 (not 403) from method security. */
    @Bean
    static PublicEndpoint testPublicPreAuthorizeIsPublic() {
        return new PublicEndpoint() {
            @Override
            public HttpMethod method() {
                return HttpMethod.GET;
            }

            @Override
            public String pattern() {
                return "/api/v1/test/public-preauth";
            }
        };
    }

    private final SessionSecurity sessionSecurity;

    TestSupportController(SessionSecurity sessionSecurity) {
        this.sessionSecurity = sessionSecurity;
    }

    @PostMapping("/login")
    Map<String, String> login(@RequestParam(defaultValue = "tester") String user, HttpServletRequest request,
                              HttpServletResponse response) {
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        sessionSecurity.establishAuthenticatedSession(auth, request, response);
        return Map.of("status", "ok");
    }

    @PostMapping("/mark")
    Map<String, String> mark(HttpServletRequest request) {
        request.getSession().setAttribute(MARKER, "user-specific-data");
        return Map.of("status", "marked");
    }

    @PostMapping("/rotate")
    Map<String, String> rotate(HttpServletRequest request) {
        sessionSecurity.rotateSessionId(request);
        return Map.of("status", "rotated");
    }

    @GetMapping("/protected")
    Map<String, String> protectedResource(Authentication auth) {
        return Map.of("user", auth.getName());
    }

    @PostMapping("/echo")
    Map<String, String> echo() {
        return Map.of("status", "ok");
    }

    @PostMapping(value = "/json", consumes = "application/json")
    Map<String, String> json(@RequestBody Map<String, Object> body) {
        return Map.of("status", "ok");
    }

    @GetMapping("/param")
    Map<String, Integer> param(@RequestParam int n) {
        return Map.of("n", n);
    }

    @GetMapping("/denied")
    Map<String, String> denied() {
        throw new AccessDeniedException("internal detail that must not leak");
    }

    @GetMapping("/unauth")
    Map<String, String> unauth() {
        throw new BadCredentialsException("internal detail that must not leak");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, String> admin() {
        return Map.of("status", "admin");
    }

    @GetMapping("/public-preauth")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, String> publicPreAuthorize() {
        return Map.of("status", "admin");
    }
}
