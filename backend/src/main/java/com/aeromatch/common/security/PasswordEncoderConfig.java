package com.aeromatch.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class PasswordEncoderConfig {

    /**
     * Argon2id (needs Bouncy Castle on the classpath). Defaults m=64 MiB, t=3, p=1, salt 16 B, hash 32 B come from
     * properties and MUST be benchmarked on the target host. Passwords are never stored or logged by this code.
     */
    @Bean
    PasswordEncoder passwordEncoder(AeroMatchSecurityProperties props) {
        AeroMatchSecurityProperties.Password p = props.password();
        return new Argon2PasswordEncoder(p.saltLength(), p.hashLength(), p.parallelism(), p.memoryKib(), p.iterations());
    }

    /**
     * Fail-closed placeholder so Spring Boot does not auto-create a default user with a generated password.
     * TODO(TB-004): replaced by the real user store.
     */
    @Bean
    UserDetailsService noUsersYet() {
        return username -> {
            throw new UsernameNotFoundException("No users");
        };
    }
}
