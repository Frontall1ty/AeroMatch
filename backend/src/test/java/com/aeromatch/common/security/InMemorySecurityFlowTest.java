package com.aeromatch.common.security;

import com.aeromatch.testsupport.InMemorySessionTestConfig;
import com.aeromatch.testsupport.MutableClockTestConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Whole security flow without a database (profile 'test', in-memory Spring Session repository). */
@SpringBootTest(properties = "aeromatch.test-support.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MutableClockTestConfig.class, InMemorySessionTestConfig.class})
class InMemorySecurityFlowTest extends AbstractSecurityFlowTest {

    @org.springframework.beans.factory.annotation.Autowired
    PermitAllRoutes permitAllRoutes;

    @org.junit.jupiter.api.Test
    void exactPermitAllSetOfTheRealContext() {
        org.assertj.core.api.Assertions.assertThat(permitAllRoutes.described()).containsExactlyInAnyOrder(
                "GET /actuator/health/**",
                "GET /api/v1/auth/csrf",
                "POST /api/v1/test/login",
                "GET /api/v1/test/public-preauth");
    }
}
