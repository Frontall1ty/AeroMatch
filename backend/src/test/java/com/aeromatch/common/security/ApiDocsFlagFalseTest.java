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

/** Flag false: OpenAPI/Swagger are 401. */
@SpringBootTest(properties = "aeromatch.security.api-docs-public=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MutableClockTestConfig.class, InMemorySessionTestConfig.class})
class ApiDocsFlagFalseTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    PermitAllRoutes routes;
    @Autowired
    AeroMatchSecurityProperties props;

    @Test
    void openApiAndSwaggerAreDenied() throws Exception {
        assertThat(props.apiDocsPublic()).isFalse();
        for (String uri : new String[] {"/api/v1/openapi", "/api/v1/openapi/swagger-config", "/api/v1/swagger-ui.html",
                "/swagger-ui/index.html", "/webjars/swagger-ui/index.css"}) {
            assertThat(mvc.perform(get(uri)).andReturn().getResponse().getStatus()).as(uri).isEqualTo(401);
        }
        assertThat(mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(routes.described()).containsExactlyInAnyOrder("GET /actuator/health/**", "GET /api/v1/auth/csrf");
    }

}
