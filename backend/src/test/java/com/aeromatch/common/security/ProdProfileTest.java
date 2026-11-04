package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aeromatch.testsupport.EmbeddedPostgresHolder;
import com.aeromatch.testsupport.MutableClockTestConfig;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Real prod profile (application-prod.yml) on embedded PostgreSQL. Forwarded-header handling behind the real
 * Caddy proxy cannot be exercised here.
 */
@SpringBootTest(properties = "aeromatch.test-support.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(MutableClockTestConfig.class)
class ProdProfileTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        EmbeddedPostgresHolder.register(registry);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    PermitAllRoutes routes;

    private static String value(String header) {
        String pair = header.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }

    /** Logs in through the test endpoint using the prod cookie names; returns the cookies to send. */
    private Cookie[] login() throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String token = value(csrf.getResponse().getHeader("Set-Cookie"));
        MvcResult login = mvc.perform(post("/api/v1/test/login").cookie(new Cookie("__Host-XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token).header("Origin", "http://localhost:3000")).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String session = login.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("__Host-AMSESSION=")).findFirst().orElseThrow();
        return new Cookie[] {new Cookie("__Host-AMSESSION", value(session)), new Cookie("__Host-XSRF-TOKEN", token)};
    }

    @Test
    void appStartsAndHealthIsAnonymousWithoutDetails() throws Exception {
        MvcResult r = mvc.perform(get("/actuator/health")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentAsString()).contains("UP").doesNotContain("components")
                .doesNotContain("details");
    }

    @Test
    void openApiAndSwaggerAreUnreachableEvenWithAValidSession() throws Exception {
        Cookie[] cookies = login();
        List<String> uris = List.of("/api/v1/openapi", "/api/v1/openapi/swagger-config", "/v3/api-docs",
                "/api/v1/swagger-ui.html", "/swagger-ui.html", "/swagger-ui/index.html",
                "/webjars/swagger-ui/index.css");
        for (String uri : uris) {
            int anon = mvc.perform(get(uri)).andReturn().getResponse().getStatus();
            int authed = mvc.perform(get(uri).cookie(cookies)).andReturn().getResponse().getStatus();
            assertThat(anon).as("anonymous " + uri).isIn(401, 404);
            assertThat(authed).as("authenticated " + uri).isIn(401, 404);
        }
        assertThat(routes.described()).noneMatch(r -> r.contains("openapi") || r.contains("swagger"));
    }

    @Test
    void onlyHealthIsExposedByActuator() throws Exception {
        Cookie[] cookies = login();
        for (String uri : List.of("/actuator/info", "/actuator/env", "/actuator/metrics", "/actuator/beans",
                "/actuator/mappings", "/actuator")) {
            assertThat(mvc.perform(get(uri)).andReturn().getResponse().getStatus()).as("anonymous " + uri)
                    .isIn(401, 404);
            assertThat(mvc.perform(get(uri).cookie(cookies)).andReturn().getResponse().getStatus())
                    .as("authenticated " + uri).isEqualTo(404);
        }
    }

    @Test
    void cookiesUseHostPrefixWithSecurePathAndNoDomain() throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String xsrf = csrf.getResponse().getHeader("Set-Cookie");
        assertThat(xsrf).startsWith("__Host-XSRF-TOKEN=").contains("Secure").contains("Path=/").doesNotContain("Domain");
        assertThat(csrf.getResponse().getCookie("__Host-XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Lax");

        String token = value(xsrf);
        MvcResult login = mvc.perform(post("/api/v1/test/login").cookie(new Cookie("__Host-XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token).header("Origin", "http://localhost:3000")).andReturn();
        String session = login.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("__Host-AMSESSION=")).findFirst().orElseThrow();
        assertThat(session).contains("Secure").contains("Path=/").contains("HttpOnly").contains("SameSite=Lax")
                .doesNotContain("Domain").doesNotContainIgnoringCase("Max-Age");
    }
}
