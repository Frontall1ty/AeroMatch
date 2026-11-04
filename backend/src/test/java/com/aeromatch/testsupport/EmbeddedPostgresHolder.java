package com.aeromatch.testsupport;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import org.springframework.test.context.DynamicPropertyRegistry;

/** One real PostgreSQL process per test JVM (zonky embedded-postgres; no Docker needed). Test scope only. */
public final class EmbeddedPostgresHolder {

    private static EmbeddedPostgres postgres;

    private EmbeddedPostgresHolder() {}

    public static synchronized EmbeddedPostgres get() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
                EmbeddedPostgres started = postgres;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        started.close();
                    } catch (IOException ignored) {
                        // best effort
                    }
                }));
            } catch (IOException e) {
                throw new IllegalStateException("Embedded PostgreSQL failed to start", e);
            }
        }
        return postgres;
    }

    public static void register(DynamicPropertyRegistry registry) {
        EmbeddedPostgres pg = get();
        registry.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }
}
