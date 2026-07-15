package com.beancounter.marketdata.providers.eodhd

import com.beancounter.marketdata.providers.eodhd.model.EodhdFundamentals
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies the EODHD Fundamentals DTOs map the provider's exact JSON keys — notably the
 * `Equity_%` sector-weight key and the ETF vs equity shape split.
 */
class EodhdFundamentalsTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `deserializes ETF Sector_Weights with Equity percent key`() {
        val json =
            """
            {
              "General": { "Type": "ETF" },
              "ETF_Data": {
                "Sector_Weights": {
                  "Technology":         { "Equity_%": "33.52294", "Relative_to_Category": "31.40656" },
                  "Financial Services": { "Equity_%": "11.97072", "Relative_to_Category": "12.83563" },
                  "Basic Materials":    { "Equity_%": "2.02624",  "Relative_to_Category": "2.1798" }
                }
              }
            }
            """.trimIndent()

        val fundamentals = objectMapper.readValue(json, EodhdFundamentals::class.java)

        val weights = fundamentals.etfData?.sectorWeights
        assertThat(weights).hasSize(3)
        assertThat(weights?.get("Technology")?.equityPct).isEqualTo("33.52294")
        assertThat(weights?.get("Financial Services")?.equityPct).isEqualTo("11.97072")
        assertThat(fundamentals.general?.type).isEqualTo("ETF")
    }

    @Test
    fun `deserializes equity sector and industry from General`() {
        val json =
            """
            {
              "General": {
                "Type": "Common Stock",
                "Sector": "Technology",
                "Industry": "Consumer Electronics"
              }
            }
            """.trimIndent()

        val fundamentals = objectMapper.readValue(json, EodhdFundamentals::class.java)

        assertThat(fundamentals.general?.sector).isEqualTo("Technology")
        assertThat(fundamentals.general?.industry).isEqualTo("Consumer Electronics")
        assertThat(fundamentals.etfData).isNull()
    }

    @Test
    fun `ignores unmodelled fundamentals fields`() {
        val json =
            """
            { "General": { "Type": "ETF", "Name": "Vanguard", "CurrencyCode": "USD" },
              "Financials": { "Balance_Sheet": {} } }
            """.trimIndent()

        val fundamentals = objectMapper.readValue(json, EodhdFundamentals::class.java)

        assertThat(fundamentals.general?.type).isEqualTo("ETF")
    }
}