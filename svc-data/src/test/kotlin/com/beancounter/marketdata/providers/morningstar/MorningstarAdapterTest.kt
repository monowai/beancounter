package com.beancounter.marketdata.providers.morningstar

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Morningstar's time series reads as price rows: the last row is the latest NAV, and a backfill
 * keeps every row it can parse.
 */
internal class MorningstarAdapterTest {
    private val asset = Asset(code = "GB00TEST0001", id = "fund-id", market = Market("MUTF", "GBP"))

    private val series =
        """
        {"TimeSeries": {"Security": [{"HistoryDetail": [
            {"EndDate": "2024-12-16", "Value": 3.10},
            {"EndDate": "not-a-date", "Value": 3.20},
            {"EndDate": "2024-12-18", "Value": 3.30}
        ]}]}}
        """.trimIndent()

    @Test
    fun `should read the last row as the latest price`() {
        val latest = MorningstarAdapter.latestPrice(series, asset)

        assertThat(latest).isNotNull
        assertThat(latest!!.priceDate).isEqualTo(LocalDate.of(2024, 12, 18))
        assertThat(latest.close).isEqualByComparingTo("3.30")
        assertThat(latest.source).isEqualTo(MorningstarPriceService.ID)
    }

    @Test
    fun `should keep every parseable row of the history and skip the rest`() {
        val history = MorningstarAdapter.history(series, asset)

        assertThat(history.map { it.priceDate })
            .containsExactly(LocalDate.of(2024, 12, 16), LocalDate.of(2024, 12, 18))
        assertThat(history).allSatisfy { assertThat(it.asset).isEqualTo(asset) }
    }

    @Test
    fun `should read no price from a response without securities or from invalid json`() {
        val empty = """{"TimeSeries": {"Security": []}}"""

        assertThat(MorningstarAdapter.latestPrice(empty, asset)).isNull()
        assertThat(MorningstarAdapter.history(empty, asset)).isEmpty()
        assertThat(MorningstarAdapter.latestPrice("not json", asset)).isNull()
        assertThat(MorningstarAdapter.history("not json", asset)).isEmpty()
    }
}