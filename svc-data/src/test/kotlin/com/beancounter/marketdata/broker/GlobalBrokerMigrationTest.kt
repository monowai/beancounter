package com.beancounter.marketdata.broker

import org.assertj.core.api.Assertions.assertThat
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.charset.StandardCharsets

/**
 * Applies the committed `global_broker` migrations, in version order, to a minimal H2
 * `broker` table. `@SpringMvcDbTest` contexts run with Flyway disabled, so this is the
 * only test that executes the migration files themselves.
 */
class GlobalBrokerMigrationTest {
    private val backfill = "V53__broker_global_broker_backfill.sql"
    private val migrationFiles = listOf("V51__global_broker.sql", "V52__global_broker_ibkr.sql", backfill)

    private fun statements(migrationFile: String): List<String> {
        val resource = ClassPathResource("db/migration/$migrationFile")
        assertThat(resource.exists())
            .withFailMessage("Migration file db/migration/%s not found on classpath", migrationFile)
            .isTrue()
        return resource.inputStream
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun migrated(): JdbcTemplate {
        val migrationSql = migrationFiles.flatMap(::statements)
        val dataSource =
            JdbcDataSource().apply {
                setUrl("jdbc:h2:mem:global-broker-${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1")
                user = "sa"
                password = ""
            }
        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute("CREATE TABLE broker (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255) NOT NULL)")
        jdbcTemplate.update("INSERT INTO broker (id, name) VALUES ('b1', 'Mine')")
        jdbcTemplate.update("INSERT INTO broker (id, name) VALUES ('b2', ' interactive brokers ')")
        jdbcTemplate.update("INSERT INTO broker (id, name) VALUES ('b3', 'TD Ameritrade')")
        jdbcTemplate.update("INSERT INTO broker (id, name) VALUES ('b4', 'IB Family Trust')")
        // Applied twice: a re-run must neither fail nor seed a broker a second time.
        repeat(2) { migrationSql.forEach { jdbcTemplate.execute(it) } }
        return jdbcTemplate
    }

    @Test
    fun `should seed each global broker once however often it runs`() {
        val codes =
            migrated().queryForList("SELECT code FROM global_broker ORDER BY code", String::class.java)

        assertThat(codes).containsExactly("ETRADE", "IBKR", "ROBINHOOD", "SCHWAB")
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
    fun `should seed a firm that trades many markets with no default market or currency`() {
        val config =
            migrated()
                .queryForList("SELECT config FROM global_broker WHERE code = 'IBKR'", String::class.java)
                .single()

        assertThat(BrokerConfigConverter().convertToEntityAttribute(checkNotNull(config)))
            .isEqualTo(BrokerConfig(aliases = listOf("Interactive Brokers", "IBKR", "IB")))
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

    private fun JdbcTemplate.firmOf(brokerId: String): String? =
        queryForList(
            "SELECT g.code FROM broker b LEFT JOIN global_broker g ON g.id = b.global_broker_id WHERE b.id = ?",
            String::class.java,
            brokerId
        ).single()

    @Test
    fun `should link an existing broker named as a firm or one of its aliases, whatever its case`() {
        val jdbcTemplate = migrated()

        assertThat(jdbcTemplate.firmOf("b2")).isEqualTo("IBKR")
        assertThat(jdbcTemplate.firmOf("b3")).isEqualTo("SCHWAB")
    }

    @Test
    fun `should not link a broker whose name only starts with an alias`() {
        assertThat(migrated().firmOf("b4")).isNull()
    }

    @Test
    fun `should keep the firm a broker already names`() {
        val jdbcTemplate = migrated()
        jdbcTemplate.update(
            "UPDATE broker SET global_broker_id = (SELECT id FROM global_broker WHERE code = 'ETRADE') WHERE id = 'b2'"
        )

        statements(backfill).forEach { jdbcTemplate.execute(it) }

        assertThat(jdbcTemplate.firmOf("b2")).isEqualTo("ETRADE")
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