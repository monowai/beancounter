package com.beancounter.marketdata.watchlist

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.charset.StandardCharsets

/**
 * Applies the committed watchlist migration to a minimal H2 schema (`system_user`, `asset`).
 * `@SpringMvcDbTest` contexts run with Flyway disabled (schema comes from `ddl-auto`), so this
 * is the only test that executes the migration file itself. It proves a re-run is a no-op and
 * that the table's own constraints reject bad rows.
 */
class WatchlistMigrationTest {
    private val migrationFile = "V43__watchlist.sql"
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun migrate() {
        val resource = ClassPathResource("db/migration/$migrationFile")
        assertThat(resource.exists())
            .withFailMessage("Migration file db/migration/%s not found on classpath", migrationFile)
            .isTrue()

        // SYSTEM_USER is a reserved word in H2 (a valid identifier in Postgres); NON_KEYWORDS
        // lets the migration's unquoted `REFERENCES system_user` parse unchanged.
        val dataSource =
            JdbcDataSource().apply {
                setUrl(
                    "jdbc:h2:mem:watchlist-migration-${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1;" +
                        "NON_KEYWORDS=SYSTEM_USER"
                )
                user = "sa"
                password = ""
            }
        jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute("CREATE TABLE system_user (id VARCHAR(255) PRIMARY KEY)")
        jdbcTemplate.execute("CREATE TABLE asset (id VARCHAR(255) PRIMARY KEY)")
        jdbcTemplate.update("INSERT INTO system_user (id) VALUES ('owner')")
        jdbcTemplate.update("INSERT INTO asset (id) VALUES ('asset-1'), ('asset-2')")

        // Comment lines are dropped before splitting: the file's prose contains semicolons.
        val statements =
            resource.inputStream
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readLines() }
                .filterNot { it.trim().startsWith("--") }
                .joinToString("\n")
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        // Applied twice: IF NOT EXISTS must make a re-run (ddl-auto may have created it first) a no-op.
        repeat(2) { statements.forEach { jdbcTemplate.execute(it) } }
    }

    private fun insert(
        id: String,
        assetId: String,
        targetPrice: String? = null,
        targetOn: String? = null
    ) = jdbcTemplate.update(
        "INSERT INTO watchlist_item (id, owner_id, asset_id, added_price, added_on, target_price, target_on) " +
            "VALUES (?, 'owner', ?, 10, DATE '2026-01-02', ?, ?)",
        id,
        assetId,
        targetPrice?.toBigDecimal(),
        targetOn?.let { java.sql.Date.valueOf(it) }
    )

    @Test
    fun `should create watchlist_item and accept valid rows`() {
        insert("plain", "asset-1")
        insert("targeted", "asset-2", targetPrice = "12", targetOn = "2026-01-02")

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM watchlist_item", Int::class.java))
            .isEqualTo(2)
    }

    @Test
    fun `should reject the same asset twice for one owner`() {
        insert("first", "asset-1")

        assertThatThrownBy { insert("second", "asset-1") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `should reject a target without its entry date`() {
        assertThatThrownBy { insert("no-date", "asset-1", targetPrice = "12") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }
}