package com.beancounter.marketdata.watchlist

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import java.nio.charset.StandardCharsets

/**
 * Applies the watchlist migrations (V43 then the target-alert migration) to a minimal H2 schema.
 * `@SpringMvcDbTest` contexts build the schema from `ddl-auto`, so only this test runs the file:
 * it proves the direction backfill, the new constraints, the alert cascade and a no-op re-run.
 */
class WatchlistAlertMigrationTest {
    private val alertMigration = "V44__watchlist_target_alerts.sql"
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun migrate() {
        // SYSTEM_USER is a reserved word in H2 (a valid identifier in Postgres). One connection
        // throughout: H2 binds a CHECK's literals to the session that created it, so a check made
        // on a since-closed connection fails every later insert as "Check constraint invalid".
        val dataSource =
            SingleConnectionDataSource(
                "jdbc:h2:mem:watchlist-alert-migration-${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1;" +
                    "NON_KEYWORDS=SYSTEM_USER",
                "sa",
                "",
                true
            )
        jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute("CREATE TABLE system_user (id VARCHAR(255) PRIMARY KEY)")
        jdbcTemplate.execute("CREATE TABLE asset (id VARCHAR(255) PRIMARY KEY)")
        jdbcTemplate.execute(
            "CREATE TABLE market_data (id VARCHAR(255) PRIMARY KEY, asset_id VARCHAR(255), " +
                "price_date DATE, close NUMERIC(19, 6))"
        )
        jdbcTemplate.update("INSERT INTO system_user (id) VALUES ('owner')")
        jdbcTemplate.update("INSERT INTO asset (id) VALUES ('priced'), ('unpriced'), ('plain')")
        jdbcTemplate.update(
            "INSERT INTO market_data (id, asset_id, price_date, close) VALUES " +
                "('m1', 'priced', DATE '2026-01-01', 200), ('m2', 'priced', DATE '2026-01-05', 100)"
        )
        apply("V43__watchlist.sql")
        // Targets already set before the migration: one above the latest close, one below the
        // added price of an unpriced asset, and an item with no target.
        insertItem("above", "priced", addedPrice = "90", targetPrice = "150")
        insertItem("below", "unpriced", addedPrice = "10", targetPrice = "8")
        insertItem("none", "plain", addedPrice = "10")
        apply(alertMigration)
        // A re-run (ddl-auto may have created the table first) must be a no-op.
        apply(alertMigration)
    }

    private fun apply(file: String) {
        val resource = ClassPathResource("db/migration/$file")
        assertThat(resource.exists())
            .withFailMessage("Migration file db/migration/%s not found on classpath", file)
            .isTrue()
        // Comment lines are dropped before splitting: the file's prose contains semicolons.
        resource.inputStream
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readLines() }
            .filterNot { it.trim().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { jdbcTemplate.execute(it) }
    }

    private fun insertItem(
        id: String,
        assetId: String,
        addedPrice: String,
        targetPrice: String? = null
    ) = jdbcTemplate.update(
        "INSERT INTO watchlist_item (id, owner_id, asset_id, added_price, added_on, target_price, target_on) " +
            "VALUES (?, 'owner', ?, ?, DATE '2026-01-02', ?, ?)",
        id,
        assetId,
        addedPrice.toBigDecimal(),
        targetPrice?.toBigDecimal(),
        targetPrice?.let { java.sql.Date.valueOf("2026-01-02") }
    )

    private fun direction(id: String): String? =
        jdbcTemplate.queryForObject("SELECT target_direction FROM watchlist_item WHERE id = ?", String::class.java, id)

    private fun insertAlert(
        id: String,
        itemId: String,
        assetId: String,
        direction: String = "ABOVE"
    ) = jdbcTemplate.update(
        "INSERT INTO watchlist_alert (id, owner_id, item_id, asset_id, target_price, direction, close, " +
            "price_date, created_at) VALUES (?, 'owner', ?, ?, 150, ?, 151, DATE '2026-01-06', CURRENT_TIMESTAMP)",
        id,
        itemId,
        assetId,
        direction
    )

    @Test
    fun `should backfill direction from the latest close`() {
        assertThat(direction("above")).isEqualTo("ABOVE")
    }

    @Test
    fun `should backfill direction from the added price when the asset has no close`() {
        assertThat(direction("below")).isEqualTo("BELOW")
    }

    @Test
    fun `should leave direction and hit date empty without a target`() {
        assertThat(direction("none")).isNull()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchlist_item WHERE target_hit_on IS NOT NULL",
                Int::class.java
            )
        ).isEqualTo(0)
    }

    @Test
    fun `should reject a target without a direction`() {
        assertThatThrownBy {
            jdbcTemplate.update("UPDATE watchlist_item SET target_direction = NULL WHERE id = 'above'")
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `should reject an unknown direction`() {
        assertThatThrownBy {
            jdbcTemplate.update("UPDATE watchlist_item SET target_direction = 'UP' WHERE id = 'above'")
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `should drop an item's alerts when the item is deleted`() {
        insertAlert("a1", "above", "priced")

        jdbcTemplate.update("DELETE FROM watchlist_item WHERE id = 'above'")

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM watchlist_alert", Int::class.java))
            .isEqualTo(0)
    }

    @Test
    fun `should reject an alert for an unknown item`() {
        assertThatThrownBy { insertAlert("a2", "no-such-item", "priced") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }
}