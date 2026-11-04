package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Unsafe security settings must stop the application from starting, with a clear message. */
class SecurityPropertiesValidationTest {

    @org.springframework.boot.test.context.TestConfiguration
    @EnableConfigurationProperties(AeroMatchSecurityProperties.class)
    static class Cfg {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Cfg.class);

    private static String rootMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void defaultsAreValid() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            AeroMatchSecurityProperties p = ctx.getBean(AeroMatchSecurityProperties.class);
            assertThat(p.apiDocsPublic()).isFalse();
            assertThat(p.cookieSecure()).isTrue();
            assertThat(p.csrfCookieName()).isEqualTo("XSRF-TOKEN");
            assertThat(p.session().cookieName()).isEqualTo("AMSESSION");
        });
    }

    @Test
    void absoluteTimeoutBelowOneMinuteFailsStartup() {
        for (String bad : new String[] {"30s", "0s", "-1h"}) {
            runner.withPropertyValues("aeromatch.security.session.absolute-timeout=" + bad).run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(rootMessages(ctx.getStartupFailure())).contains("absolute-timeout must be between 1 minute and 7 days");
            });
        }
    }

    @Test
    void absoluteTimeoutAboveSevenDaysFailsStartup() {
        runner.withPropertyValues("aeromatch.security.session.absolute-timeout=8d").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(rootMessages(ctx.getStartupFailure())).contains("absolute-timeout must be between 1 minute and 7 days");
        });
    }

    @Test
    void boundariesAreAccepted() {
        runner.withPropertyValues("aeromatch.security.session.absolute-timeout=1m").run(ctx -> assertThat(ctx).hasNotFailed());
        runner.withPropertyValues("aeromatch.security.session.absolute-timeout=7d").run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void hostPrefixedSessionCookieWithoutSecureFailsStartup() {
        runner.withPropertyValues("aeromatch.security.cookie-secure=false",
                "aeromatch.security.session.cookie-name=__Host-AMSESSION").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(rootMessages(ctx.getStartupFailure())).contains("__Host-").contains("cookie-secure=true");
        });
    }

    @Test
    void hostPrefixedCsrfCookieWithoutSecureFailsStartup() {
        runner.withPropertyValues("aeromatch.security.cookie-secure=false",
                "aeromatch.security.csrf-cookie-name=__Host-XSRF-TOKEN").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(rootMessages(ctx.getStartupFailure())).contains("__Host-XSRF-TOKEN");
        });
    }

    @Test
    void hostPrefixWithSecureStarts() {
        runner.withPropertyValues("aeromatch.security.session.cookie-name=__Host-AMSESSION",
                "aeromatch.security.csrf-cookie-name=__Host-XSRF-TOKEN").run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
