package com.aeromatch.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.aeromatch.testsupport.EmbeddedPostgresHolder;
import com.aeromatch.testsupport.MutableClockTestConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The same security flow against the REAL Spring Session JDBC store on a real PostgreSQL (embedded, no Docker),
 * with the schema created by Flyway V2, plus store-specific checks (idle timeout, persistence, indexes).
 */
@SpringBootTest(properties = "aeromatch.test-support.enabled=true")
@AutoConfigureMockMvc
@Import(MutableClockTestConfig.class)
class JdbcSecurityFlowTest extends AbstractSecurityFlowTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        EmbeddedPostgresHolder.register(registry);
    }

    @Autowired
    JdbcTemplate jdbc;

    private void ageSession(Jar jar, Duration idleFor) {
        long last = System.currentTimeMillis() - idleFor.toMillis();
        int updated = jdbc.update("update spring_session set last_access_time = ?, expiry_time = ? where session_id = ?",
                last, last + 1_800_000L, sessionIdOf(jar.get(sessionCookieName)));
        assertThat(updated).isEqualTo(1);
    }

    @Test
    void sessionIsStoredInPostgresWithPrincipalAndAuthenticationTime() throws Exception {
        Jar jar = login();
        String id = sessionIdOf(jar.get(sessionCookieName));
        Map<String, Object> row = jdbc.queryForMap("select * from spring_session where session_id = ?", id);
        assertThat(row.get("principal_name")).isEqualTo("tester");
        assertThat(((Number) row.get("max_inactive_interval")).intValue()).isEqualTo(1800);
        Integer attrs = jdbc.queryForObject(
                "select count(*) from spring_session_attributes a join spring_session s on s.primary_id = a.session_primary_id"
                        + " where s.session_id = ? and a.attribute_name = ?", Integer.class, id,
                SessionSecurity.AUTHENTICATED_AT);
        assertThat(attrs).isEqualTo(1);
    }

    @Test
    void allSessionsOfAUserCanBeFoundByPrincipalName() throws Exception {
        login();
        login();
        FindByIndexNameSessionRepository<?> repo = (FindByIndexNameSessionRepository<?>) sessions;
        assertThat(repo.findByPrincipalName("tester")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void idleSessionOlderThan30MinutesIsDenied() throws Exception {
        Jar jar = login();
        ageSession(jar, Duration.ofMinutes(31));
        assertError(doGet(jar, "/api/v1/test/protected"), 401, "UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("select count(*) from spring_session where session_id = ?", Integer.class,
                sessionIdOf(jar.get(sessionCookieName)))).isZero();
    }

    @Test
    void sessionIdleFor29MinutesIsAllowedAndIdleTimerResets() throws Exception {
        Jar jar = login();
        ageSession(jar, Duration.ofMinutes(29));
        assertThat(doGet(jar, "/api/v1/test/protected").getResponse().getStatus()).isEqualTo(200);
        Long last = jdbc.queryForObject("select last_access_time from spring_session where session_id = ?", Long.class,
                sessionIdOf(jar.get(sessionCookieName)));
        assertThat(System.currentTimeMillis() - last).isLessThan(60_000L); // was 29 min, reset by the request
        ageSession(jar, Duration.ofMinutes(29)); // a further 29 min after the reset is fine again
        assertThat(doGet(jar, "/api/v1/test/protected").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void v2MigrationCreatedTablesAndIndexes() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public' and table_name like 'spring_session%'",
                String.class);
        assertThat(tables).contains("spring_session", "spring_session_attributes");
        List<String> indexes = jdbc.queryForList("select indexname from pg_indexes where tablename = 'spring_session'",
                String.class);
        assertThat(indexes).contains("spring_session_ix1", "spring_session_ix2", "spring_session_ix3");
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '2' and success", Integer.class))
                .isEqualTo(1);
    }
}
