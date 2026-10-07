package com.beancounter.marketdata.providers.sec

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Extraction rules over the SEC `companyfacts` shape: per duration tag pick the `10-K` / `FY`
 * entry with the latest `end` (latest `filed` on a tie), `Revenues` falls back to
 * `RevenueFromContractWithCustomerExcludingAssessedTax`, a missing tag is a null field rather
 * than a failure, and shares come from the `dei` instant tag.
 */
class SecFundamentalsParserTest {
    private val parser = SecFundamentalsParser()

    private fun fixture(name: String) = ClassPathResource("mock/sec/$name").file.readText()

    private fun entry(
        end: String,
        value: String,
        fy: Int = 2025,
        fp: String = "FY",
        form: String = "10-K",
        filed: String = "2025-10-31"
    ) = """{"start":"2024-01-01","end":"$end","val":$value,"fy":$fy,"fp":"$fp","form":"$form","filed":"$filed"}"""

    private fun tag(
        unit: String,
        vararg entries: String
    ) = """{"units":{"$unit":[${entries.joinToString(",")}]}}"""

    private fun facts(
        usGaap: Map<String, String>,
        dei: Map<String, String> = emptyMap()
    ): String {
        val gaap = usGaap.entries.joinToString(",") { """"${it.key}":${it.value}""" }
        val deiJson = dei.entries.joinToString(",") { """"${it.key}":${it.value}""" }
        return """{"cik":1,"entityName":"X","facts":{"dei":{$deiJson},"us-gaap":{$gaap}}}"""
    }

    @Test
    fun `should pick the latest fiscal-year 10-K entry for every tag`() {
        val snapshot = parser.parse(fixture("companyfacts-AAPL.json"))

        assertThat(snapshot).isNotNull
        assertThat(snapshot!!.fiscalYearEnd).isEqualTo(LocalDate.of(2025, 9, 27))
        assertThat(snapshot.fiscalYear).isEqualTo(2025)
        assertThat(snapshot.epsDiluted).isEqualByComparingTo(BigDecimal("7.46"))
        assertThat(snapshot.revenue).isEqualByComparingTo(BigDecimal("416161000000"))
        assertThat(snapshot.netIncome).isEqualByComparingTo(BigDecimal("112010000000"))
        assertThat(snapshot.dividendsPerShare).isEqualByComparingTo(BigDecimal("1.02"))
        assertThat(snapshot.sharesOutstanding).isEqualTo(14773123000L)
    }

    @Test
    fun `should ignore quarterly entries even when they come from a 10-K filing`() {
        val json =
            facts(
                mapOf(
                    "EarningsPerShareDiluted" to
                        tag(
                            "USD/shares",
                            entry(end = "2025-09-27", value = "7.46"),
                            entry(end = "2025-12-27", value = "2.40", fp = "Q1", form = "10-K"),
                            entry(end = "2025-12-27", value = "2.41", fp = "Q1", form = "10-Q")
                        )
                )
            )

        val snapshot = parser.parse(json)

        assertThat(snapshot!!.epsDiluted).isEqualByComparingTo(BigDecimal("7.46"))
        assertThat(snapshot.fiscalYearEnd).isEqualTo(LocalDate.of(2025, 9, 27))
    }

    @Test
    fun `should prefer the most recently filed entry when two share the same period end`() {
        val json =
            facts(
                mapOf(
                    "NetIncomeLoss" to
                        tag(
                            "USD",
                            entry(end = "2024-09-28", value = "100", fy = 2024, filed = "2024-11-01"),
                            entry(end = "2024-09-28", value = "101", fy = 2025, filed = "2025-10-31")
                        )
                )
            )

        assertThat(parser.parse(json)!!.netIncome).isEqualByComparingTo(BigDecimal("101"))
    }

    @Test
    fun `should fall back to contract revenue when Revenues is absent`() {
        val json =
            facts(
                mapOf(
                    "EarningsPerShareDiluted" to tag("USD/shares", entry(end = "2025-09-27", value = "7.46")),
                    "RevenueFromContractWithCustomerExcludingAssessedTax" to
                        tag("USD", entry(end = "2025-09-27", value = "500"))
                )
            )

        assertThat(parser.parse(json)!!.revenue).isEqualByComparingTo(BigDecimal("500"))
    }

    @Test
    fun `should leave a field null when its tag is missing`() {
        val json =
            facts(
                mapOf("EarningsPerShareDiluted" to tag("USD/shares", entry(end = "2025-09-27", value = "7.46")))
            )

        val snapshot = parser.parse(json)

        assertThat(snapshot!!.epsDiluted).isEqualByComparingTo(BigDecimal("7.46"))
        assertThat(snapshot.revenue).isNull()
        assertThat(snapshot.netIncome).isNull()
        assertThat(snapshot.dividendsPerShare).isNull()
        assertThat(snapshot.sharesOutstanding).isNull()
    }

    @Test
    fun `should take shares outstanding from the latest dei instant entry`() {
        val json =
            facts(
                usGaap = mapOf("NetIncomeLoss" to tag("USD", entry(end = "2025-09-27", value = "1"))),
                dei =
                    mapOf(
                        "EntityCommonStockSharesOutstanding" to
                            tag(
                                "shares",
                                """{"end":"2025-10-17","val":14773123000,"fy":2025,"fp":"FY","form":"10-K","filed":"2025-10-31"}""",
                                """{"end":"2024-10-18","val":15115823000,"fy":2024,"fp":"FY","form":"10-K","filed":"2024-11-01"}"""
                            )
                    )
            )

        assertThat(parser.parse(json)!!.sharesOutstanding).isEqualTo(14773123000L)
    }

    @Test
    fun `should return null when no fiscal-year 10-K entry exists at all`() {
        val json =
            facts(
                mapOf(
                    "EarningsPerShareDiluted" to
                        tag("USD/shares", entry(end = "2025-12-27", value = "2.41", fp = "Q1", form = "10-Q"))
                )
            )

        assertThat(parser.parse(json)).isNull()
    }
}