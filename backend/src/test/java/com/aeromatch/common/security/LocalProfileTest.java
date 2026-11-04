package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aeromatch.testsupport.EmbeddedPostgresHolder;
import com.aeromatch.testsupport.MutableClockTestConfig;
import jakarta.servlet.http.Cookie;
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

/** The real local profile (application-local.yml): plain-http cookies for the Next.js dev rewrite, docs public. */
@SpringBootTest(properties = "aeromatch.test-support.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(MutableClockTestConfig.class)
class LocalProfileTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        EmbeddedPostgresHolder.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void cookiesAreNotSecureAndKeepStandardNames() throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String xsrf = csrf.getResponse().getHeader("Set-Cookie");
        assertThat(xsrf).startsWith("XSRF-TOKEN=").contains("Path=/").doesNotContain("Secure");
        String token = xsrf.substring(xsrf.indexOf('=') + 1, xsrf.indexOf(';'));

        MvcResult login = mvc.perform(post("/api/v1/test/login").cookie(new Cookie("XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token).header("Origin", "http://localhost:3000")).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String session = login.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("AMSESSION=")).findFirst().orElseThrow();
        assertThat(session).contains("HttpOnly").contains("SameSite=Lax").doesNotContain("Secure");
    }

    @Test
    void docsArePublicOnlyInLocal() throws Exception {
        assertThat(mvc.perform(get("/api/v1/openapi")).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/api/v1/shipments")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
