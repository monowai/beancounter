package com.beancounter.marketdata.payslip

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
 * Applies the committed recurring payslip migration to a minimal H2 schema. `@SpringMvcDbTest`
 * contexts take their schema from `ddl-auto`, so this is the only test that runs the file. It
 * proves a re-run is a no-op and that the table rejects a second row for one user and bad values.
 */
class RecurringPayslipMigrationTest {
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun migrate() {
        val resource = ClassPathResource("db/migration/V56__recurring_payslip.sql")
        assertThat(resource.exists()).isTrue()
        val dataSource =
            JdbcDataSource().apply {
                setUrl(
                    "jdbc:h2:mem:payslip-migration-${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1;" +
                        "NON_KEYWORDS=SYSTEM_USER"
                )
                user = "sa"
                password = ""
            }
        jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute("CREATE TABLE system_user (id VARCHAR(255) PRIMARY KEY)")
        jdbcTemplate.execute("CREATE TABLE portfolio (id VARCHAR(255) PRIMARY KEY)")
        jdbcTemplate.update("INSERT INTO system_user (id) VALUES ('owner'), ('other')")
        jdbcTemplate.update("INSERT INTO portfolio (id) VALUES ('pf')")

        val statements =
            resource.inputStream
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readLines() }
                .filterNot { it.trim().startsWith("--") }
                .joinToString("\n")
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        repeat(2) { statements.forEach { jdbcTemplate.execute(it) } }
    }

    private fun insert(
        id: String,
        owner: String = "owner",
        gross: String = "10000",
        tax: String = "0",
        day: Int = 25
    ) = jdbcTemplate.update(
        "INSERT INTO recurring_payslip " +
            "(id, owner_id, portfolio_id, cash_asset_id, gross_salary, tax, day_of_month, next_pay_date) " +
            "VALUES (?, ?, 'pf', 'cash', ?, ?, ?, DATE '2026-12-25')",
        id,
        owner,
        gross.toBigDecimal(),
        tax.toBigDecimal(),
        day
    )

    @Test
    fun `should accept one recurring payslip per user`() {
        insert("rp-1")
        insert("rp-2", owner = "other")

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recurring_payslip", Int::class.java))
            .isEqualTo(2)
        assertThatThrownBy { insert("rp-3") }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `should reject a day of month outside 1 to 31 and non-positive amounts`() {
        assertThatThrownBy { insert("rp-day0", day = 0) }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insert("rp-day32", day = 32) }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insert("rp-gross", gross = "0") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insert("rp-tax", tax = "-1") }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}