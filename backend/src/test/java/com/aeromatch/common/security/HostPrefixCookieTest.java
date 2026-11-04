package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aeromatch.testsupport.InMemorySessionTestConfig;
import com.aeromatch.testsupport.MutableClockTestConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** A production profile may rename the session cookie to the __Host- form; it must stay Secure, Path=/, no Domain. */
@SpringBootTest(properties = {"aeromatch.test-support.enabled=true",
        "aeromatch.security.session.cookie-name=__Host-AMSESSION",
        "aeromatch.security.csrf-cookie-name=__Host-XSRF-TOKEN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MutableClockTestConfig.class, InMemorySessionTestConfig.class})
class HostPrefixCookieTest {

    @Autowired
    MockMvc mvc;

    @Test
    void hostPrefixedCookieSatisfiesHostRules() throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String cookie = csrf.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).startsWith("__Host-XSRF-TOKEN=").contains("Secure").contains("Path=/").doesNotContain("Domain");
        String token = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
        // JSON contract is unchanged by the cookie rename
        MvcResult body = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        assertThat(body.getResponse().getContentAsString()).contains("\"headerName\":\"X-XSRF-TOKEN\"");
        MvcResult login = mvc.perform(post("/api/v1/test/login").cookie(new Cookie("__Host-XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token).header("Origin", "http://localhost:3000")).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String session = login.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith("__Host-AMSESSION=")).findFirst().orElseThrow();
        assertThat(session).contains("Secure").contains("Path=/").contains("HttpOnly").doesNotContain("Domain");
    }
}
