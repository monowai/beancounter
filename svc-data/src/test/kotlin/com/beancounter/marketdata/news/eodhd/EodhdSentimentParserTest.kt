package com.beancounter.marketdata.news.eodhd

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pins the shape tolerance of [EodhdSentimentParser]: the `/api/sentiments` map is keyed by the
 * symbols EODHD chose to answer for, and individual points can be malformed without the whole
 * batch being thrown away.
 */
internal class EodhdSentimentParserTest {
    @Test
    fun `parses one point list per symbol key`() {
        val json =
            """
            {"AAPL.US":[{"date":"2026-10-06","count":27,"normalized":0.8937},
                        {"date":"2026-10-05","count":25,"normalized":0.807}],
             "TSCO.LSE":[{"date":"2026-10-05","count":3,"normalized":0.5133}]}
            """.trimIndent()

        val parsed = EodhdSentimentParser.parse(json)

        assertThat(parsed).containsOnlyKeys("AAPL.US", "TSCO.LSE")
        assertThat(parsed.getValue("AAPL.US"))
            .containsExactly(
                SentimentPoint(LocalDate.parse("2026-10-06"), 27, BigDecimal("0.8937")),
                SentimentPoint(LocalDate.parse("2026-10-05"), 25, BigDecimal("0.807"))
            )
        assertThat(parsed.getValue("TSCO.LSE")).hasSize(1)
    }

    @Test
    fun `empty object yields an empty map`() {
        assertThat(EodhdSentimentParser.parse("{}")).isEmpty()
    }

    @Test
    fun `points with a non-numeric normalized value are dropped`() {
        val json =
            """{"AAPL.US":[{"date":"2026-10-06","count":27,"normalized":"n/a"},
                           {"date":"2026-10-05","count":25,"normalized":0.807}]}"""

        val parsed = EodhdSentimentParser.parse(json)

        assertThat(parsed.getValue("AAPL.US"))
            .containsExactly(SentimentPoint(LocalDate.parse("2026-10-05"), 25, BigDecimal("0.807")))
    }

    @Test
    fun `points with an unparseable date are dropped`() {
        val json = """{"AAPL.US":[{"date":"yesterday","count":27,"normalized":0.5}]}"""

        assertThat(EodhdSentimentParser.parse(json).getValue("AAPL.US")).isEmpty()
    }

    @Test
    fun `symbol keys whose value is not an array yield no points`() {
        val json = """{"AAPL.US":{"error":"no data"},"MSFT.US":[]}"""

        val parsed = EodhdSentimentParser.parse(json)

        assertThat(parsed.getValue("AAPL.US")).isEmpty()
        assertThat(parsed.getValue("MSFT.US")).isEmpty()
    }

    @Test
    fun `malformed body throws so the batch is counted as failed`() {
        assertThatThrownBy { EodhdSentimentParser.parse("not json") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}