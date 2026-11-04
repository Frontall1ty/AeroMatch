package com.aeromatch.common.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Security settings (prefix {@code aeromatch.security}). Defaults are the safest values; profiles relax them explicitly. */
@ConfigurationProperties("aeromatch.security")
public record AeroMatchSecurityProperties(
        @DefaultValue("http://localhost:3000") List<String> allowedOrigins,
        @DefaultValue("true") boolean cookieSecure,
        @DefaultValue("false") boolean apiDocsPublic,
        @DefaultValue("XSRF-TOKEN") String csrfCookieName,
        @DefaultValue Session session,
        @DefaultValue Password password) {

    static final Duration MIN_ABSOLUTE_TIMEOUT = Duration.ofMinutes(1);
    static final Duration MAX_ABSOLUTE_TIMEOUT = Duration.ofDays(7);
    private static final String HOST_PREFIX = "__Host-";

    /** Fails startup on unsafe combinations (clear message instead of a silently weakened policy). */
    public AeroMatchSecurityProperties {
        Duration abs = session.absoluteTimeout();
        if (abs == null || abs.compareTo(MIN_ABSOLUTE_TIMEOUT) < 0 || abs.compareTo(MAX_ABSOLUTE_TIMEOUT) > 0) {
            throw new IllegalStateException("aeromatch.security.session.absolute-timeout must be between 1 minute and 7 days, got: "
                    + abs);
        }
        if (!cookieSecure) {
            for (String name : new String[] {session.cookieName(), csrfCookieName}) {
                if (name != null && name.startsWith(HOST_PREFIX)) {
                    throw new IllegalStateException("Cookie '" + name + "' uses the __Host- prefix, which requires "
                            + "aeromatch.security.cookie-secure=true");
                }
            }
        }
    }

    public record Session(
            @DefaultValue("AMSESSION") String cookieName,
            @DefaultValue("12h") Duration absoluteTimeout) {}

    /** Argon2id cost parameters. Must be benchmarked on the target host. memoryKib is in KiB (65536 = 64 MiB). */
    public record Password(
            @DefaultValue("65536") int memoryKib,
            @DefaultValue("3") int iterations,
            @DefaultValue("1") int parallelism,
            @DefaultValue("16") int saltLength,
            @DefaultValue("32") int hashLength) {}
}
