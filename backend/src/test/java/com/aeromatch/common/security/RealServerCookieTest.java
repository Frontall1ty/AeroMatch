package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.aeromatch.testsupport.InMemorySessionTestConfig;
import com.aeromatch.testsupport.MutableClockTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** Raw Set-Cookie headers written by the real servlet container (Tomcat), not by MockMvc. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "aeromatch.test-support.enabled=true")
@ActiveProfiles("test")
@Import({MutableClockTestConfig.class, InMemorySessionTestConfig.class})
class RealServerCookieTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void rawCookieHeadersCarryTheRequiredAttributes() {
        ResponseEntity<String> csrf = rest.getForEntity("/api/v1/auth/csrf", String.class);
        List<String> csrfCookies = csrf.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(csrfCookies).hasSize(1);
        String xsrf = csrfCookies.get(0);
        assertThat(xsrf).startsWith("XSRF-TOKEN=").contains("Path=/").contains("SameSite=Lax").contains("Secure")
                .doesNotContain("HttpOnly");
        String token = xsrf.substring("XSRF-TOKEN=".length(), xsrf.indexOf(';'));

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + token);
        h.add("X-XSRF-TOKEN", token);
        h.add(HttpHeaders.ORIGIN, "http://localhost:3000");
        ResponseEntity<String> login = rest.postForEntity("/api/v1/test/login", new HttpEntity<>(h), String.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        List<String> cookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        String session = cookies.stream().filter(c -> c.startsWith("AMSESSION=")).findFirst().orElseThrow();
        assertThat(session).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/").contains("Secure")
                .doesNotContainIgnoringCase("Max-Age").doesNotContainIgnoringCase("Expires")
                .doesNotContainIgnoringCase("Domain");
        String rotated = cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        assertThat(rotated).doesNotContain(token).contains("SameSite=Lax");
    }
}
