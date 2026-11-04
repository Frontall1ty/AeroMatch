package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aeromatch.testsupport.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Security behaviour that must hold regardless of the session store. Concrete subclasses run it against an
 * in-memory repository (no DB) and against the real JDBC repository on PostgreSQL.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
abstract class AbstractSecurityFlowTest {

    static final String ORIGIN = "http://localhost:3000";
    static final String CSRF_COOKIE = "XSRF-TOKEN";
    static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    MockMvc mvc;
    @Autowired
    MutableClock clock;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    SessionRepository sessions;
    @Value("${aeromatch.security.session.cookie-name}")
    String sessionCookieName;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    // ---------------------------------------------------------------- helpers

    /** Minimal browser cookie jar driven by Set-Cookie headers. */
    static final class Jar {
        final Map<String, String> cookies = new LinkedHashMap<>();

        void update(MvcResult result) {
            for (String header : result.getResponse().getHeaders("Set-Cookie")) {
                String pair = header.split(";", 2)[0];
                int eq = pair.indexOf('=');
                String name = pair.substring(0, eq);
                String value = pair.substring(eq + 1);
                if (header.toLowerCase().contains("max-age=0") || value.isEmpty()) {
                    cookies.remove(name);
                } else {
                    cookies.put(name, value);
                }
            }
        }

        Jar copy() {
            Jar j = new Jar();
            j.cookies.putAll(cookies);
            return j;
        }

        String get(String name) {
            return cookies.get(name);
        }
    }

    static List<String> setCookies(MvcResult r, String name) {
        return r.getResponse().getHeaders("Set-Cookie").stream().filter(h -> h.startsWith(name + "=")).toList();
    }

    static String sessionIdOf(String cookieValue) {
        return new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
    }

    static String cookieValueFor(String sessionId) {
        return Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
    }

    MockHttpServletRequestBuilder withJar(MockHttpServletRequestBuilder b, Jar jar) {
        jar.cookies.forEach((n, v) -> b.cookie(new Cookie(n, v)));
        return b;
    }

    MvcResult doGet(Jar jar, String uri) throws Exception {
        MvcResult r = mvc.perform(withJar(get(uri), jar)).andReturn();
        jar.update(r);
        return r;
    }

    /** POST with explicit control over the CSRF header and Origin. Null means "omit". */
    MvcResult doPost(Jar jar, String uri, String csrfToken, String origin) throws Exception {
        MockHttpServletRequestBuilder b = withJar(post(uri), jar);
        if (csrfToken != null) {
            b.header(CSRF_HEADER, csrfToken);
        }
        if (origin != null) {
            b.header("Origin", origin);
        }
        MvcResult r = mvc.perform(b).andReturn();
        jar.update(r);
        return r;
    }

    JsonNode json(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    void assertError(MvcResult r, int status, String errorCode) throws Exception {
        assertThat(r.getResponse().getStatus()).isEqualTo(status);
        assertThat(r.getResponse().getContentType()).startsWith("application/json");
        JsonNode body = json(r);
        assertThat(body.get("errorCode").asText()).isEqualTo(errorCode);
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("internal detail").doesNotContain("Exception");
    }

    String fetchCsrf(Jar jar) throws Exception {
        MvcResult r = doGet(jar, "/api/v1/auth/csrf");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        String token = json(r).get("token").asText();
        assertThat(jar.get(CSRF_COOKIE)).isEqualTo(token);
        return token;
    }

    /** Test-only login: GET csrf, POST /api/v1/test/login with token + allowed Origin. Returns the jar. */
    Jar login() throws Exception {
        return login(new Jar());
    }

    Jar login(Jar jar) throws Exception {
        String csrf = fetchCsrf(jar);
        MvcResult r = doPost(jar, "/api/v1/test/login", csrf, ORIGIN);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(jar.get(sessionCookieName)).isNotNull();
        return jar;
    }

    Session sessionOf(Jar jar) {
        return (Session) sessions.findById(sessionIdOf(jar.get(sessionCookieName)));
    }

    // ---------------------------------------------------------------- a) deny by default

    @Test
    void unauthenticatedRequestsGet401JsonEvenForUnmappedPaths() throws Exception {
        for (String uri : List.of("/api/v1/shipments", "/api/v1/does/not/exist", "/api/v1/test/protected",
                "/api/v1/auth/logout", "/actuator/info", "/actuator/env", "/anything-else")) {
            assertError(mvc.perform(get(uri)).andReturn(), 401, "UNAUTHENTICATED");
        }
    }

    @Test
    void unauthenticatedUnsafeMethodsWithValidCsrfAreStill401() throws Exception {
        Jar jar = new Jar();
        String csrf = fetchCsrf(jar);
        assertError(doPost(jar, "/api/v1/not/mapped", csrf, ORIGIN), 401, "UNAUTHENTICATED");
        assertError(doPost(jar, "/api/v1/test/echo", csrf, ORIGIN), 401, "UNAUTHENTICATED");
    }

    @Test
    void healthIsPublic() throws Exception {
        assertThat(mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousRequestsDoNotCreateSessions() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/shipments")).andReturn();
        assertThat(setCookies(r, sessionCookieName)).isEmpty();
    }

    // ---------------------------------------------------------------- b) CSRF

    @Test
    void csrfEndpointIssuesCookieAndMatchingJson() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(r);
        assertThat(body.get("headerName").asText()).isEqualTo(CSRF_HEADER);
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.size()).isEqualTo(2);
        List<String> cookie = setCookies(r, CSRF_COOKIE);
        assertThat(cookie).hasSize(1);
        assertThat(cookie.get(0)).startsWith(CSRF_COOKIE + "=" + body.get("token").asText())
                .containsIgnoringCase("Path=/")
                .doesNotContainIgnoringCase("HttpOnly"); // JavaScript must be able to read it
        // MockMvc does not render servlet-cookie attributes such as SameSite into the header text;
        // the raw header over a real HTTP server is asserted in RealServerCookieTest.
        assertThat(r.getResponse().getCookie(CSRF_COOKIE).getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(r.getResponse().getCookie(CSRF_COOKIE).isHttpOnly()).isFalse();
    }

    @Test
    void csrfCookieIsIssuedOnAnyResponseNotOnlyTheCsrfEndpoint() throws Exception {
        MvcResult r = mvc.perform(get("/actuator/health")).andReturn();
        assertThat(setCookies(r, CSRF_COOKIE)).hasSize(1);
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        Jar jar = login();
        assertError(doPost(jar, "/api/v1/test/echo", null, ORIGIN), 403, "CSRF_INVALID");
    }

    @Test
    void loginStylePostWithoutCsrfTokenIsRejected() throws Exception {
        assertError(doPost(new Jar(), "/api/v1/test/login", null, ORIGIN), 403, "CSRF_INVALID");
        Jar jar = new Jar();
        fetchCsrf(jar);
        assertError(doPost(jar, "/api/v1/test/login", null, ORIGIN), 403, "CSRF_INVALID"); // cookie alone is not enough
    }

    @Test
    void postWithWrongCsrfTokenIsRejected() throws Exception {
        Jar jar = login();
        assertError(doPost(jar, "/api/v1/test/echo", "not-the-token", ORIGIN), 403, "CSRF_INVALID");
        String other = fetchCsrf(new Jar()); // a genuine token, but for a different browser
        assertError(doPost(jar, "/api/v1/test/echo", other, ORIGIN), 403, "CSRF_INVALID");
    }

    @Test
    void validTokenFromForeignOriginIsRejected() throws Exception {
        Jar jar = login();
        String csrf = jar.get(CSRF_COOKIE);
        assertError(doPost(jar, "/api/v1/test/echo", csrf, "https://evil.example"), 403, "CSRF_INVALID");
        assertError(doPost(jar, "/api/v1/test/echo", csrf, "null"), 403, "CSRF_INVALID");
        assertError(doPost(jar, "/api/v1/test/echo", csrf, "http://localhost:3000.evil.example"), 403, "CSRF_INVALID");
    }

    @Test
    void foreignRefererIsRejectedWhenOriginIsAbsent() throws Exception {
        Jar jar = login();
        MockHttpServletRequestBuilder b = withJar(post("/api/v1/test/echo"), jar)
                .header(CSRF_HEADER, jar.get(CSRF_COOKIE)).header("Referer", "https://evil.example/page");
        assertError(mvc.perform(b).andReturn(), 403, "CSRF_INVALID");
        MockHttpServletRequestBuilder ok = withJar(post("/api/v1/test/echo"), jar)
                .header(CSRF_HEADER, jar.get(CSRF_COOKIE)).header("Referer", ORIGIN + "/some/page");
        assertThat(mvc.perform(ok).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void validTokenAndAllowedOriginSucceeds() throws Exception {
        Jar jar = login();
        MvcResult r = doPost(jar, "/api/v1/test/echo", jar.get(CSRF_COOKIE), ORIGIN);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void validTokenWithoutOriginOrRefererStillPassesBecauseTokenIsChecked() throws Exception {
        Jar jar = login();
        MvcResult r = doPost(jar, "/api/v1/test/echo", jar.get(CSRF_COOKIE), null);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void csrfTokenRotatesWhenAuthenticationHappens() throws Exception {
        Jar jar = new Jar();
        String before = fetchCsrf(jar);
        assertThat(doPost(jar, "/api/v1/test/login", before, ORIGIN).getResponse().getStatus()).isEqualTo(200);
        String after = fetchCsrf(jar);
        assertThat(after).isNotEqualTo(before);
        // the pre-login token is useless now, even combined with the new cookie
        assertError(doPost(jar, "/api/v1/test/echo", before, ORIGIN), 403, "CSRF_INVALID");
        assertThat(doPost(jar, "/api/v1/test/echo", after, ORIGIN).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void logoutViaGetIsRejectedAndKeepsTheSession() throws Exception {
        Jar jar = login();
        MvcResult r = doGet(jar, "/api/v1/auth/logout");
        assertThat(r.getResponse().getStatus()).isIn(404, 405);
        assertThat(doGet(jar, "/api/v1/test/protected").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void logoutWithoutCsrfTokenIsRejectedAndKeepsTheSession() throws Exception {
        Jar jar = login();
        assertError(doPost(jar, "/api/v1/auth/logout", null, ORIGIN), 403, "CSRF_INVALID");
        assertThat(doGet(jar, "/api/v1/test/protected").getResponse().getStatus()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- c) session fixation

    @Test
    void sessionIdChangesOnAuthentication() throws Exception {
        // attacker-planted, pre-authentication session known to the victim's browser
        Session planted = sessions.createSession();
        sessions.save(planted);
        String plantedId = planted.getId();
        Jar jar = new Jar();
        jar.cookies.put(sessionCookieName, cookieValueFor(plantedId));
        String csrf = fetchCsrf(jar);

        MvcResult r = doPost(jar, "/api/v1/test/login", csrf, ORIGIN);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);

        String newId = sessionIdOf(jar.get(sessionCookieName));
        assertThat(newId).isNotEqualTo(plantedId);
        assertThat(sessions.findById(plantedId)).as("pre-auth id must be dead").isNull();
        assertThat(doGet(jar, "/api/v1/test/protected").getResponse().getStatus()).isEqualTo(200);

        // replaying the old (fixated) id gets nothing
        Jar attacker = new Jar();
        attacker.cookies.put(sessionCookieName, cookieValueFor(plantedId));
        assertError(doGet(attacker, "/api/v1/test/protected"), 401, "UNAUTHENTICATED");
    }

    // ---------------------------------------------------------------- d) absolute timeout

    @Test
    void absoluteTimeoutAllowsActivityUntil11h59AndDeniesAt12h01() throws Exception {
        Jar jar = login();
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofHours(3)); // continuous activity: every request would reset the idle timer
            assertThat(doGet(jar, "/api/v1/test/protected").getResponse().getStatus()).isEqualTo(200);
        }
        clock.advance(Duration.ofHours(2).plusMinutes(59)); // 11h59m after login
        assertThat(doGet(jar, "/api/v1/test/protected").getResponse().getStatus()).isEqualTo(200);

        String idAtExpiry = sessionIdOf(jar.get(sessionCookieName));
        Jar probe = jar.copy();
        clock.advance(Duration.ofMinutes(2)); // 12h01m after login
        assertError(doGet(probe, "/api/v1/test/protected"), 401, "SESSION_EXPIRED");
        assertThat(sessions.findById(idAtExpiry)).as("expired session must be invalidated server-side").isNull();
        // the same cookie is dead afterwards
        assertError(doGet(jar, "/api/v1/test/protected"), 401, "UNAUTHENTICATED");
    }

    @Test
    void reLoginAfterExpiryGetsAFreshAuthenticatedAt() throws Exception {
        Jar jar = login();
        Instant first = (Instant) sessionOf(jar).getAttribute(SessionSecurity.AUTHENTICATED_AT);
        assertThat(first).isEqualTo(MutableClock.START);

        clock.advance(Duration.ofHours(12).plusMinutes(1));
        assertError(doGet(jar, "/api/v1/test/protected"), 401, "SESSION_EXPIRED");

        Jar fresh = new Jar();
        login(fresh);
        Instant second = (Instant) sessionOf(fresh).getAttribute(SessionSecurity.AUTHENTICATED_AT);
        assertThat(second).isEqualTo(MutableClock.START.plus(Duration.ofHours(12).plusMinutes(1)));
        clock.advance(Duration.ofHours(11));
        assertThat(doGet(fresh, "/api/v1/test/protected").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void sessionWithoutAuthenticatedAtIsRejectedFailClosed() throws Exception {
        Jar jar = login();
        Session s = sessionOf(jar);
        String id = s.getId();
        s.removeAttribute(SessionSecurity.AUTHENTICATED_AT);
        sessions.save(s);
        assertError(doGet(jar, "/api/v1/test/protected"), 401, "UNAUTHENTICATED");
        assertThat(sessions.findById(id)).isNull();
    }

    @Test
    void sessionIdRotationDoesNotResetTheAbsoluteDeadline() throws Exception {
        Jar jar = login();
        String idBefore = sessionIdOf(jar.get(sessionCookieName));
        clock.advance(Duration.ofHours(11));
        assertThat(doPost(jar, "/api/v1/test/rotate", jar.get(CSRF_COOKIE), ORIGIN).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(sessionIdOf(jar.get(sessionCookieName))).isNotEqualTo(idBefore);
        Object at = sessionOf(jar).getAttribute(SessionSecurity.AUTHENTICATED_AT);
        assertThat(at).isEqualTo(MutableClock.START);

        clock.advance(Duration.ofHours(1).plusMinutes(1)); // 12h01m after the ORIGINAL login
        assertError(doGet(jar, "/api/v1/test/protected"), 401, "SESSION_EXPIRED");
    }

    @Test
    void forgedSessionCookieGetsNothing() throws Exception {
        Jar forged = new Jar();
        forged.cookies.put(sessionCookieName, cookieValueFor("forged-id"));
        assertError(doGet(forged, "/api/v1/test/protected"), 401, "UNAUTHENTICATED");
    }

    // ---------------------------------------------------------------- expired session vs public routes (fix 2)

    @Test
    void expiredSessionCookieStillAllowsPublicRoutes() throws Exception {
        for (String publicUri : List.of("/api/v1/auth/csrf", "/actuator/health")) {
            Jar jar = login(); // fresh session each time: the first request with an expired cookie destroys it
            clock.advance(Duration.ofHours(12).plusMinutes(1));
            assertThat(doGet(jar, publicUri).getResponse().getStatus()).as(publicUri).isEqualTo(200);
        }
        Jar jar = login();
        clock.advance(Duration.ofHours(12).plusMinutes(1));
        assertError(doGet(jar, "/api/v1/test/protected"), 401, "SESSION_EXPIRED");
    }

    @Test
    void canLogInAgainUsingAnExpiredSessionCookie() throws Exception {
        Jar jar = login();
        String expiredCookie = jar.get(sessionCookieName);
        clock.advance(Duration.ofHours(12).plusMinutes(1));

        String csrf = fetchCsrf(jar); // public route, expired cookie still presented
        MvcResult r = doPost(jar, "/api/v1/test/login", csrf, ORIGIN);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(jar.get(sessionCookieName)).isNotEqualTo(expiredCookie);
        assertThat(doGet(jar, "/api/v1/test/protected").getResponse().getStatus()).isEqualTo(200);
        Object at = sessionOf(jar).getAttribute(SessionSecurity.AUTHENTICATED_AT);
        assertThat(at).isEqualTo(clock.instant());
    }

    // ---------------------------------------------------------------- AccessDenied: anonymous 401, authenticated 403 (fix 3)

    @Test
    void methodSecurityDenialOnPublicRouteIs401ForAnonymousAnd403ForAuthenticated() throws Exception {
        assertError(doGet(new Jar(), "/api/v1/test/public-preauth"), 401, "UNAUTHENTICATED");
        Jar jar = login();
        assertError(doGet(jar, "/api/v1/test/public-preauth"), 403, "FORBIDDEN");
    }

    // ---------------------------------------------------------------- malformed requests are 4xx, never 500 (fix 4)

    @Test
    void malformedJsonIs400() throws Exception {
        Jar jar = login();
        MockHttpServletRequestBuilder b = withJar(post("/api/v1/test/json"), jar)
                .header(CSRF_HEADER, jar.get(CSRF_COOKIE)).header("Origin", ORIGIN)
                .contentType("application/json").content("{not json");
        assertError(mvc.perform(b).andReturn(), 400, "MALFORMED_REQUEST");
    }

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        Jar jar = login();
        MockHttpServletRequestBuilder b = withJar(post("/api/v1/test/json"), jar)
                .header(CSRF_HEADER, jar.get(CSRF_COOKIE)).header("Origin", ORIGIN)
                .contentType("text/plain").content("hello");
        assertError(mvc.perform(b).andReturn(), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void missingAndInvalidParametersAre400() throws Exception {
        Jar jar = login();
        assertError(doGet(jar, "/api/v1/test/param"), 400, "VALIDATION_ERROR");
        assertError(doGet(jar, "/api/v1/test/param?n=abc"), 400, "BAD_REQUEST");
        assertThat(doGet(jar, "/api/v1/test/param?n=5").getResponse().getStatus()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- re-login on an authenticated session (fix 7)

    @Test
    void loggingInAsAnotherUserOnAnExistingSessionDropsTheFirstUsersData() throws Exception {
        Jar jar = login(); // user A ("tester")
        String csrf = jar.get(CSRF_COOKIE);
        assertThat(doPost(jar, "/api/v1/test/mark", csrf, ORIGIN).getResponse().getStatus()).isEqualTo(200);
        String idA = sessionIdOf(jar.get(sessionCookieName));
        Object markerA = sessionOf(jar).getAttribute("aeromatch.test.marker");
        assertThat(markerA).isEqualTo("user-specific-data");

        MvcResult r = doPost(jar, "/api/v1/test/login?user=userB", csrf, ORIGIN);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);

        String idB = sessionIdOf(jar.get(sessionCookieName));
        assertThat(idB).isNotEqualTo(idA);
        assertThat(sessions.findById(idA)).as("old session is destroyed").isNull();
        Object markerB = sessionOf(jar).getAttribute("aeromatch.test.marker");
        assertThat(markerB).as("user A's attributes must not carry over").isNull();
        assertThat(json(doGet(jar, "/api/v1/test/protected")).get("user").asText()).isEqualTo("userB");
    }

    // ---------------------------------------------------------------- f) cookie attributes

    @Test
    void sessionCookieAttributes() throws Exception {
        Jar jar = new Jar();
        String csrf = fetchCsrf(jar);
        MvcResult r = doPost(jar, "/api/v1/test/login", csrf, ORIGIN);
        List<String> cookies = setCookies(r, sessionCookieName);
        assertThat(cookies).hasSize(1);
        String c = cookies.get(0);
        assertThat(c).startsWith("AMSESSION=")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .contains("Secure") // cookie-secure defaults to true
                .doesNotContainIgnoringCase("Max-Age")
                .doesNotContainIgnoringCase("Expires")
                .doesNotContainIgnoringCase("Domain");
    }

    // ---------------------------------------------------------------- g) exceptions never become 500

    @Test
    void accessDeniedAndAuthenticationExceptionsFromControllersAreNot500() throws Exception {
        Jar jar = login();
        assertError(doGet(jar, "/api/v1/test/denied"), 403, "FORBIDDEN");
        assertError(doGet(jar, "/api/v1/test/unauth"), 401, "UNAUTHENTICATED");
        assertError(doGet(jar, "/api/v1/test/admin"), 403, "FORBIDDEN"); // @PreAuthorize (method security)
    }

    @Test
    void unmappedPathWhenAuthenticatedIs404Not500() throws Exception {
        Jar jar = login();
        assertError(doGet(jar, "/api/v1/does/not/exist"), 404, "NOT_FOUND");
    }

    // ---------------------------------------------------------------- h) logout

    @Test
    void logoutInvalidatesTheServerSideSession() throws Exception {
        Jar jar = login();
        String oldCookie = jar.get(sessionCookieName);
        Jar stolenCopy = jar.copy();
        assertThat(doGet(jar, "/api/v1/test/protected").getResponse().getStatus()).isEqualTo(200);

        MvcResult r = doPost(jar, "/api/v1/auth/logout", jar.get(CSRF_COOKIE), ORIGIN);
        assertThat(r.getResponse().getStatus()).isEqualTo(204);
        assertThat(r.getResponse().getContentAsString()).isEmpty();
        assertThat(r.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(h -> h.startsWith(sessionCookieName + "=") && h.contains("Max-Age=0"))).isTrue();
        assertThat(jar.get(sessionCookieName)).isNull();

        assertThat(sessions.findById(sessionIdOf(oldCookie))).isNull();
        // a captured copy of the old cookie no longer works
        assertError(doGet(stolenCopy, "/api/v1/test/protected"), 401, "UNAUTHENTICATED");
    }

    // ---------------------------------------------------------------- j) headers

    @Test
    void securityHeadersArePresentOnApiResponses() throws Exception {
        Jar jar = login();
        for (MvcResult r : List.of(doGet(jar, "/api/v1/test/protected"), doGet(new Jar(), "/api/v1/test/protected"),
                doGet(new Jar(), "/api/v1/auth/csrf"))) {
            var res = r.getResponse();
            assertThat(res.getHeader("Cache-Control")).contains("no-store");
            assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(res.getHeader("X-Frame-Options")).isEqualTo("DENY");
            assertThat(res.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
            assertThat(res.getHeader("Content-Security-Policy"))
                    .isEqualTo("default-src 'none'; frame-ancestors 'none'");
        }
    }

    @Test
    void hstsOnlyOnSecureRequests() throws Exception {
        assertThat(mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getHeader("Strict-Transport-Security"))
                .isNull();
        assertThat(mvc.perform(get("/api/v1/auth/csrf").secure(true)).andReturn().getResponse()
                .getHeader("Strict-Transport-Security")).contains("max-age=31536000");
    }
}
