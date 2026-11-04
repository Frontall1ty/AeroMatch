package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aeromatch.testsupport.InMemorySessionTestConfig;
import com.aeromatch.testsupport.MutableClockTestConfig;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Profile properties drive the cookie name and the Secure flag (local dev: Secure off; prod: __Host- prefix). */
@SpringBootTest(properties = {"aeromatch.test-support.enabled=true", "aeromatch.security.cookie-secure=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MutableClockTestConfig.class, InMemorySessionTestConfig.class})
class CookieProfileOverrideTest {

    @Autowired
    MockMvc mvc;

    @Test
    void secureFlagIsOffWhenConfiguredForLocalProfile() throws Exception {
        MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        String cookie = csrf.getResponse().getHeader("Set-Cookie");
        String token = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
        assertThat(cookie).doesNotContain("Secure");

        MvcResult login = mvc.perform(post("/api/v1/test/login").cookie(new Cookie("XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token).header("Origin", "http://localhost:3000")).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        List<String> cookies = login.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).isNotEmpty();
        for (String c : cookies) {
            assertThat(c).doesNotContain("Secure");
        }
        assertThat(cookies).anyMatch(c -> c.startsWith("AMSESSION=") && c.contains("HttpOnly"));
    }
}
