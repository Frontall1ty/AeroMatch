package com.aeromatch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the real configuration against PostgreSQL: Flyway migrates, Hibernate validates,
 * health is UP and OpenAPI is served. Skipped automatically when Docker is unavailable
 * (as on the local dev machine); runs in CI.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostgresBootTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessionRepository sessionRepository; // the bean is the JDBC repository; asserted below

    @Test
    void flywayBaselineApplied() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);
    }

    @Test
    void healthIsUp() {
        var res = rest.getForEntity("/actuator/health", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("UP");
    }

    @Test
    void openApiIsNotPublicByDefault() {
        var res = rest.getForEntity("/api/v1/openapi", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void sessionTablesExistFromFlywayV2() {
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'"
                        + " and table_name in ('spring_session', 'spring_session_attributes')", Integer.class);
        assertThat(tables).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void sessionWrittenThroughJdbcRepositoryIsPersisted() {
        assertThat(sessionRepository).isInstanceOf(JdbcIndexedSessionRepository.class);
        Session session = sessionRepository.createSession();
        session.setAttribute("probe", "value");
        sessionRepository.save(session);

        Integer rows = jdbc.queryForObject(
                "select count(*) from spring_session where session_id = ?", Integer.class, session.getId());
        assertThat(rows).isEqualTo(1);
        Session reloaded = sessionRepository.findById(session.getId());
        assertThat(reloaded).isNotNull();
        assertThat((String) reloaded.getAttribute("probe")).isEqualTo("value");

        sessionRepository.deleteById(session.getId());
        assertThat(sessionRepository.findById(session.getId())).isNull();
    }

    @Test
    void protectedEndpointsRejectAnonymousRequests() {
        var res = rest.getForEntity("/api/v1/anything", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
