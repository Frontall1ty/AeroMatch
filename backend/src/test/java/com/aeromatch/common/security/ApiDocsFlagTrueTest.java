package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.aeromatch.testsupport.InMemorySessionTestConfig;
import com.aeromatch.testsupport.MutableClockTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Flag true: OpenAPI is served, but only the specific springdoc paths are opened. */
@SpringBootTest(properties = "aeromatch.security.api-docs-public=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MutableClockTestConfig.class, InMemorySessionTestConfig.class})
class ApiDocsFlagTrueTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    PermitAllRoutes routes;
    @Autowired
    AeroMatchSecurityProperties props;

    @Test
    void openApiIsServedOnlyForTheSpecificPaths() throws Exception {
        var res = mvc.perform(get("/api/v1/openapi")).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader("Content-Security-Policy")).isNull(); // Swagger exception while public (TB-005)
        // not wholesale: unrelated /webjars and other API paths stay denied
        assertThat(mvc.perform(get("/webjars/other-lib/x.js")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/v1/shipments")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(routes.described()).contains("GET /api/v1/openapi", "GET /webjars/swagger-ui/**")
                .doesNotContain("GET /webjars/**");
    }

}
