package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class Argon2PasswordEncoderTest {

    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder(
            new AeroMatchSecurityProperties(List.of(), true, false, "XSRF-TOKEN",
                    new AeroMatchSecurityProperties.Session("AMSESSION", Duration.ofHours(12)),
                    new AeroMatchSecurityProperties.Password(65536, 3, 1, 16, 32)));

    @Test
    void hashesWithArgon2idAndDocumentedParameters() {
        String hash = encoder.encode("correct horse battery staple");
        assertThat(hash).startsWith("$argon2id$").contains("m=65536,t=3,p=1");
        assertThat(hash).doesNotContain("correct horse");
    }

    @Test
    void matchesCorrectPasswordAndRejectsWrongOne() {
        String hash = encoder.encode("correct horse battery staple");
        assertThat(encoder.matches("correct horse battery staple", hash)).isTrue();
        assertThat(encoder.matches("Correct horse battery staple", hash)).isFalse();
        assertThat(encoder.matches("", hash)).isFalse();
    }

    @Test
    void sameInputGivesDifferentHashesBecauseOfRandomSalt() {
        assertThat(encoder.encode("same-password-123")).isNotEqualTo(encoder.encode("same-password-123"));
    }
}
