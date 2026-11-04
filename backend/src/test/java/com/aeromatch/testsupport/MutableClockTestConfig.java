package com.aeromatch.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the application Clock (the @Primary bean wins injection) with a controllable one. */
@TestConfiguration
public class MutableClockTestConfig {
    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }
}
