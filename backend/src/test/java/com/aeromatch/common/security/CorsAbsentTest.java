package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import com.aeromatch.testsupport.InMemorySessionTestConfig;
import com.aeromatch.testsupport.MutableClockTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Single-origin (D-012): there is no CORS configuration, so no cross-origin access is ever granted. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MutableClockTestConfig.class, InMemorySessionTestConfig.class})
class CorsAbsentTest {

    @Autowired
    MockMvc mvc;

    @Test
    void noAccessControlHeadersOnPreflightOrCrossOriginRequests() throws Exception {
        for (String uri : new String[] {"/api/v1/auth/csrf", "/actuator/health", "/api/v1/shipments"}) {
            MvcResult pre = mvc.perform(options(uri).header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "GET")).andReturn();
            MvcResult cross = mvc.perform(get(uri).header("Origin", "http://localhost:3000")).andReturn();
            for (MvcResult r : new MvcResult[] {pre, cross}) {
                assertThat(r.getResponse().getHeader("Access-Control-Allow-Origin")).as(uri).isNull();
                assertThat(r.getResponse().getHeader("Access-Control-Allow-Credentials")).as(uri).isNull();
            }
            assertThat(pre.getResponse().getStatus()).as(uri).isNotEqualTo(200);
        }
    }
}
