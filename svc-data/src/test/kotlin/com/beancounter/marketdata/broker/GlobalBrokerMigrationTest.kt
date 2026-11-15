package com.beancounter.marketdata.broker

import org.assertj.core.api.Assertions.assertThat
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.charset.StandardCharsets

/**
 * Applies the committed `global_broker` migration to a minimal H2 `broker` table.
 * `@SpringMvcDbTest` contexts run with Flyway disabled, so this is the only test
 * that executes the migration file itself.
 */
class GlobalBrokerMigrationTest {
    private val migrationFile = "V51__global_broker.sql"

    private fun migrated(): JdbcTemplate {
        val resource = ClassPathResource("db/migration/$migrationFile")
        assertThat(resource.exists())
            .withFailMessage("Migration file db/migration/%s not found on classpath", migrationFile)
            .isTrue()
        val dataSource =
            JdbcDataSource().apply {
                setUrl("jdbc:h2:mem:global-broker-${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1")
                user = "sa"
                password = ""
            }
        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute("CREATE TABLE broker (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255) NOT NULL)")
        jdbcTemplate.update("INSERT INTO broker (id, name) VALUES ('b1', 'Mine')")
        val migrationSql = resource.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        // Applied twice: a re-run must neither fail nor seed a broker a second time.
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
    fun `should seed each global broker once however often it runs`() {
        val codes =
            migrated().queryForList("SELECT code FROM global_broker ORDER BY code", String::class.java)

        assertThat(codes).containsExactly("ETRADE", "ROBINHOOD", "SCHWAB")
    }

    @Test
    fun `should seed configuration the service can read`() {
        val config =
            migrated()
                .queryForList("SELECT config FROM global_broker WHERE code = 'SCHWAB'", String::class.java)
                .single()

        assertThat(BrokerConfigConverter().convertToEntityAttribute(checkNotNull(config)))
            .isEqualTo(
                BrokerConfig(
                    aliases = listOf("Charles Schwab", "TD Ameritrade", "TD"),
                    market = "US",
                    currency = "USD"
                )
            )
    }

    @Test
    fun `should leave an existing broker without a global broker`() {
        val linked =
            migrated().queryForObject(
                "SELECT global_broker_id FROM broker WHERE id = 'b1'",
                String::class.java
            )

        assertThat(linked).isNull()
    }

    @Test
    fun `should refuse a broker linked to a global broker that does not exist`() {
        val jdbcTemplate = migrated()

        val linking =
            runCatching {
                jdbcTemplate.update("UPDATE broker SET global_broker_id = 'nobody' WHERE id = 'b1'")
            }

        assertThat(linking.isFailure).isTrue()
    }

    @Test
    fun `should read configuration that carries a setting this version does not know`() {
        val config =
            BrokerConfigConverter().convertToEntityAttribute(
                """{"aliases":["TD"],"market":"US","currency":"USD","settlementDays":2}"""
            )

        assertThat(config).isEqualTo(BrokerConfig(aliases = listOf("TD"), market = "US", currency = "USD"))
    }
}