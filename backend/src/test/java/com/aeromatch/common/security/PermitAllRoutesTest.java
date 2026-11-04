package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Validation of PublicEndpoint / permitAll routes: explicit method, specific non-empty pattern. */
class PermitAllRoutesTest {

    @Test
    void acceptsExplicitMethodAndSpecificPattern() {
        assertThat(new PermitAllRoutes.Route(HttpMethod.POST, "/api/v1/auth/login").toString())
                .isEqualTo("POST /api/v1/auth/login");
    }

    @Test
    void rejectsMissingMethod() {
        assertThatThrownBy(() -> new PermitAllRoutes.Route(null, "/api/v1/x")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicit HTTP method");
    }

    @Test
    void rejectsEmptyOrMatchEverythingPatterns() {
        for (String bad : new String[] {null, "", "  ", "/", "/**", "/*", "api/v1/x"}) {
            assertThatThrownBy(() -> new PermitAllRoutes.Route(HttpMethod.GET, bad))
                    .as("pattern '%s'", bad).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("specific path pattern");
        }
    }
}
