package com.beancounter.marketdata.apikey

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.charset.StandardCharsets

/**
 * Applies the committed `api_key.parent_id` migration to a minimal H2 `api_key` table.
 * `@SpringMvcDbTest` contexts run with Flyway disabled, so this is the only test that
 * executes the migration file itself.
 */
class ApiKeyParentMigrationTest {
    private val migrationFile = "V48__api_key_parent.sql"

    private fun migrated(): JdbcTemplate {
        val resource = ClassPathResource("db/migration/$migrationFile")
        assertThat(resource.exists())
            .withFailMessage("Migration file db/migration/%s not found on classpath", migrationFile)
            .isTrue()
        val dataSource =
            JdbcDataSource().apply {
                setUrl("jdbc:h2:mem:api-key-parent-${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1")
                user = "sa"
                password = ""
            }
        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute("CREATE TABLE api_key (id VARCHAR(36) PRIMARY KEY, name VARCHAR(255) NOT NULL)")
        jdbcTemplate.update("INSERT INTO api_key (id, name) VALUES ('device', 'phone')")
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
    fun `should add a nullable parent_id and be safe to rerun`() {
        val jdbcTemplate = migrated()

        assertThat(
            jdbcTemplate.queryForObject("SELECT parent_id FROM api_key WHERE id = 'device'", String::class.java)
        ).isNull()
    }

    @Test
    fun `should only accept an existing key as the parent and drop children with it`() {
        val jdbcTemplate = migrated()
        jdbcTemplate.update("INSERT INTO api_key (id, name, parent_id) VALUES ('widget', 'w', 'device')")

        assertThatThrownBy {
            jdbcTemplate.update("INSERT INTO api_key (id, name, parent_id) VALUES ('orphan', 'o', 'missing')")
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        jdbcTemplate.update("DELETE FROM api_key WHERE id = 'device'")
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM api_key", Int::class.java)).isZero()
    }
}