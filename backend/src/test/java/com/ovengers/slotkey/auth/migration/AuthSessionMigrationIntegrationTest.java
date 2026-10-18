package com.ovengers.slotkey.auth.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSessionMigrationIntegrationTest {

    @Test
    void v24DeletesLegacyRefreshTokensBeforeRequiringSessionForeignKey() throws Exception {
        try (MySQLContainer<?> mysql = mysqlContainer()) {
            mysql.start();
            Flyway v23 = flyway(mysql).target("23").load();
            v23.migrate();

            try (Connection connection = connection(mysql);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO member (
                            email, nickname, role, status, balance,
                            created_at, updated_at, withdrawn_at, email_verified_at
                        ) VALUES (
                            'legacy@example.test', 'legacy', 'USER', 'ACTIVE', 0,
                            CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL, CURRENT_TIMESTAMP(6)
                        )
                        """);
                statement.executeUpdate("""
                        INSERT INTO refresh_token (member_id, token_hash, expires_at)
                        VALUES (1, REPEAT('a', 64), DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY))
                        """);
            }

            flyway(mysql).load().migrate();

            try (Connection connection = connection(mysql);
                 Statement statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery(
                        "SELECT COUNT(*) FROM refresh_token")) {
                    result.next();
                    assertThat(result.getInt(1)).isZero();
                }
                try (ResultSet result = statement.executeQuery("""
                        SELECT IS_NULLABLE FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'refresh_token'
                          AND column_name = 'auth_session_id'
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("NO");
                }
                try (ResultSet result = statement.executeQuery("""
                        SELECT COUNT(*) FROM information_schema.key_column_usage
                        WHERE table_schema = DATABASE()
                          AND table_name = 'refresh_token'
                          AND column_name = 'auth_session_id'
                          AND referenced_table_name = 'auth_session'
                        """)) {
                    result.next();
                    assertThat(result.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    void allMigrationsApplyToAnEmptyDatabase() throws Exception {
        try (MySQLContainer<?> mysql = mysqlContainer()) {
            mysql.start();
            Flyway flyway = flyway(mysql).load();

            flyway.migrate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("24");
            try (Connection connection = connection(mysql);
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COUNT(*) FROM information_schema.tables "
                                 + "WHERE table_schema = DATABASE() "
                                 + "AND table_name = 'auth_session'")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }

    private MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("slotkey_migration_test")
                .withUsername("test")
                .withPassword("test");
    }

    private FluentConfiguration flyway(MySQLContainer<?> mysql) {
        return Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true);
    }

    private Connection connection(MySQLContainer<?> mysql) throws Exception {
        return DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()
        );
    }
}
