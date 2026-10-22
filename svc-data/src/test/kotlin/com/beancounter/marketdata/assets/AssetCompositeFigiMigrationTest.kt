package com.beancounter.marketdata.assets

import org.assertj.core.api.Assertions.assertThat
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.charset.StandardCharsets

/**
 * Applies the committed `asset.composite_figi` migration to a minimal H2 `asset` table.
 * `@SpringMvcDbTest` contexts run with Flyway disabled, so this is the only test that
 * executes the migration file itself.
 */
class AssetCompositeFigiMigrationTest {
    private val migrationFile = "V46__asset_composite_figi.sql"

    @Test
    fun `should add a nullable composite_figi and be safe to rerun`() {
        val resource = ClassPathResource("db/migration/$migrationFile")
        assertThat(resource.exists())
            .withFailMessage("Migration file db/migration/%s not found on classpath", migrationFile)
            .isTrue()

        val dataSource =
            JdbcDataSource().apply {
                setUrl("jdbc:h2:mem:asset-composite-figi-${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1")
                user = "sa"
                password = ""
            }
        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute("CREATE TABLE asset (id VARCHAR(40) PRIMARY KEY, code VARCHAR(255) NOT NULL)")
        jdbcTemplate.update("INSERT INTO asset (id, code) VALUES ('existing', 'ABC')")

        val migrationSql = resource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        // Applied twice: IF NOT EXISTS must make a re-run (ddl-auto may have added it first) a no-op.
        repeat(2) {
            migrationSql
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { jdbcTemplate.execute(it) }
        }

        val compositeFigi =
            jdbcTemplate.queryForObject(
                "SELECT composite_figi FROM asset WHERE id = 'existing'",
                String::class.java
            )
        assertThat(compositeFigi).isNull()
    }
}