package com.harshith.event_booking.integration;

import com.harshith.event_booking.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replaces the default contextLoads() test. If the app starts at all, Hibernate's "validate" already
 * agreed with the schema; these checks add what Flyway built and which settings the tests run with.
 */
class MigrationIntegrationTest extends IntegrationTestBase {

    @Autowired
    Environment environment;

    @Test
    void runsOnPostgres17_withEveryFlywayMigrationApplied() {
        assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("17");

        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2", "3", "4", "5");
        assertThat(history).extracting(row -> row.get("success")).containsOnly(true);
    }

    @Test
    void seedData_has8EventsWith50SeatsEach_rowsAandBPricedHigher() {
        // V2 adds 3 events, V5 adds 5 more.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForList("SELECT count(*) FROM seats GROUP BY event_id", Integer.class))
                .hasSize(8).containsOnly(50);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM seats WHERE seat_row IN ('A','B') AND price_cents = 7500", Integer.class))
                .isEqualTo(8 * 20);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM seats WHERE seat_row IN ('C','D','E') AND price_cents = 4500", Integer.class))
                .isEqualTo(8 * 30);
    }

    @Test
    void usesTheTestOnlyJwtSecret_andCannotSeeLocalProperties() {
        assertThat(environment.getProperty("app.jwt.secret"))
                .isEqualTo("test-only-dummy-jwt-secret-do-not-use-in-production");
        // Tests run from target/ (see pom.xml), so "./local.properties" is never found or loaded.
        assertThat(Path.of("local.properties").toAbsolutePath().getParent().getFileName().toString())
                .isEqualTo("target");
        assertThat(Path.of("local.properties")).doesNotExist();
    }
}
