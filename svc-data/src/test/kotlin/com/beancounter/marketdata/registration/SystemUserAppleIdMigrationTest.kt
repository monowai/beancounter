package com.beancounter.marketdata.registration

import org.assertj.core.api.Assertions.assertThat
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.charset.StandardCharsets

/**
 * Applies the committed `system_user.apple_id` migration to a minimal H2 `system_user`
 * table. `@SpringMvcDbTest` contexts run with Flyway disabled, so this is the only test
 * that executes the migration file itself.
 */
class SystemUserAppleIdMigrationTest {
    private val migrationFile = "V50__system_user_apple_id.sql"

    private fun migrated(): JdbcTemplate {
        val resource = ClassPathResource("db/migration/$migrationFile")
        assertThat(resource.exists())
            .withFailMessage("Migration file db/migration/%s not found on classpath", migrationFile)
            .isTrue()
        val dataSource =
            JdbcDataSource().apply {
                setUrl(
                    "jdbc:h2:mem:system-user-apple-${System.nanoTime()};MODE=MySQL;NON_KEYWORDS=SYSTEM_USER;DB_CLOSE_DELAY=-1"
                )
                user = "sa"
                password = ""
            }
        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute("CREATE TABLE system_user (id VARCHAR(36) PRIMARY KEY, email VARCHAR(255) NOT NULL)")
        jdbcTemplate.update("INSERT INTO system_user (id, email) VALUES ('u1', 'before@test.com')")
        val migrationSql = resource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        // Applied twice: IF NOT EXISTS must make a re-run (ddl-auto may have added it first) a no-op.
        repeat(2) {
            migrationSql
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { jdbcTemplate.execute(it) }
        }
        return jdbcTemplate
    }

    @Test
    fun `should give existing users a blank apple_id and be safe to rerun`() {
        val jdbcTemplate = migrated()

        assertThat(
            jdbcTemplate.queryForObject("SELECT apple_id FROM system_user WHERE id = 'u1'", String::class.java)
        ).isEmpty()
    }

    @Test
    fun `should default apple_id to blank for a user inserted without one`() {
        val jdbcTemplate = migrated()
        jdbcTemplate.update("INSERT INTO system_user (id, email) VALUES ('u2', 'after@test.com')")

        assertThat(
            jdbcTemplate.queryForObject("SELECT apple_id FROM system_user WHERE id = 'u2'", String::class.java)
        ).isEmpty()
    }
}