package com.aeromatch.testsupport;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

/** Session repository for the no-database 'test' profile. The real JDBC store is covered by the embedded-PostgreSQL tests. */
@TestConfiguration
@EnableSpringHttpSession
public class InMemorySessionTestConfig {
    @Bean
    MapSessionRepository sessionRepository() {
        return new MapSessionRepository(new ConcurrentHashMap<>());
    }
}
