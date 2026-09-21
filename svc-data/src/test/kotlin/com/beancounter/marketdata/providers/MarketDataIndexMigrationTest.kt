package com.beancounter.marketdata.providers

import org.assertj.core.api.Assertions.assertThat
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.charset.StandardCharsets

/**
 * Verifies the `market_data(asset_id, price_date DESC)` migration creates
 * `idx_market_data_asset_price`, and that its SQL is valid on H2 as well as Postgres.
 *
 * [MarketDataRepo.findLatestByAssetInAndPriceDateLessThanEqual]'s correlated
 * `MAX(priceDate)` subquery per asset was measured seq-scanning the full 339k-row table
 * (370ms for an 8-asset probe on kauri); the only existing indexes are the pkey and the
 * (source, asset_id, priceDate) unique constraint, which has `source` leading and so is
 * unusable for a per-asset lookup.
 *
 * Regular `@SpringMvcDbTest` contexts run with `spring.flyway.enabled: false` - the H2
 * schema is generated straight from the JPA entity via `ddl-auto: update` (see
 * application.yml) - so they never exercise migration files, and the full local
 * migration chain isn't H2-clean (several earlier migrations use Postgres-only syntax
 * such as JSONB). This test instead builds a minimal `market_data` table matching the
 * entity's real columns and applies this migration's actual SQL file to it directly, so
 * the committed artifact - not a re-typed copy of it - is what's under test.
 */
class MarketDataIndexMigrationTest {
    private val migrationFile = "V36__market_data_asset_price_index.sql"

    @Test
    fun `should create asset and price date index from migration sql`() {
        val resource = ClassPathResource("db/migration/$migrationFile")
        assertThat(resource.exists())
            .withFailMessage("Migration file db/migration/%s not found on classpath", migrationFile)
            .isTrue()

        val dataSource =
            JdbcDataSource().apply {
                setUrl("jdbc:h2:mem:market-data-index-test-${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1")
                user = "sa"
                password = ""
            }
        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute(
            "CREATE TABLE market_data (" +
                "id VARCHAR(40) PRIMARY KEY, " +
                "asset_id VARCHAR(40) NOT NULL, " +
                "price_date DATE NOT NULL)"
        )

        val migrationSql = resource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        migrationSql
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { jdbcTemplate.execute(it) }

        val indexNames =
            jdbcTemplate.queryForList(
                "SELECT index_name FROM information_schema.indexes WHERE UPPER(table_name) = 'MARKET_DATA'",
                String::class.java
            )

        assertThat(indexNames)
            .anyMatch { it.equals("idx_market_data_asset_price", ignoreCase = true) }
    }
}