package com.beancounter.marketdata.watchlist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import java.nio.charset.StandardCharsets

/**
 * Applies V43, V44 then the direction-review migration to a minimal H2 schema and proves which
 * targets it flags: armed targets set on or before the day V44 ran, nothing else.
 */
class WatchlistDirectionReviewMigrationTest {
    private val reviewMigration = "V45__watchlist_direction_review.sql"
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun migrate() {
        // One connection throughout: see WatchlistAlertMigrationTest.
        val dataSource =
            SingleConnectionDataSource(
                "jdbc:h2:mem:watchlist-direction-review-${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1;" +
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
        jdbcTemplate.execute("CREATE TABLE flyway_schema_history (version VARCHAR(50), installed_on TIMESTAMP)")
        jdbcTemplate.update("INSERT INTO system_user (id) VALUES ('owner')")
        jdbcTemplate.update("INSERT INTO asset (id) VALUES ('a1'), ('a2'), ('a3'), ('a4')")
        apply("V43__watchlist.sql")
        insertItem("backfilled", "a1", targetOn = "2026-01-02")
        insertItem("hit", "a2", targetOn = "2026-01-02")
        insertItem("none", "a3", targetOn = null)
        apply("V44__watchlist_target_alerts.sql")
        jdbcTemplate.update("INSERT INTO flyway_schema_history VALUES ('44', TIMESTAMP '2026-01-03 09:00:00')")
        jdbcTemplate.update("UPDATE watchlist_item SET target_hit_on = DATE '2026-01-03' WHERE id = 'hit'")
        // Set by the app after V44: its direction came from a live price on one basis.
        insertItem("later", "a4", targetOn = "2026-01-04", direction = "ABOVE")
        apply(reviewMigration)
        apply(reviewMigration)
    }

    private fun apply(file: String) {
        ClassPathResource("db/migration/$file")
            .inputStream
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
        targetOn: String?,
        direction: String? = null
    ) {
        val columns = if (direction == null) "" else ", target_direction"
        val values = if (direction == null) "" else ", '$direction'"
        jdbcTemplate.update(
            "INSERT INTO watchlist_item (id, owner_id, asset_id, added_price, added_on, target_price, " +
                "target_on$columns) VALUES (?, 'owner', ?, 10, DATE '2026-01-01', ?, ?$values)",
            id,
            assetId,
            targetOn?.let { 12.toBigDecimal() },
            targetOn?.let { java.sql.Date.valueOf(it) }
        )
    }

    private fun unverified(id: String): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT direction_unverified FROM watchlist_item WHERE id = ?",
            Boolean::class.java,
            id
        ) == true

    @Test
    fun `should flag an armed target backfilled by V44`() {
        assertThat(unverified("backfilled")).isTrue()
    }

    @Test
    fun `should not flag a target already reached`() {
        assertThat(unverified("hit")).isFalse()
    }

    @Test
    fun `should not flag an item without a target`() {
        assertThat(unverified("none")).isFalse()
    }

    @Test
    fun `should not flag a target set after V44 ran`() {
        assertThat(unverified("later")).isFalse()
    }
}