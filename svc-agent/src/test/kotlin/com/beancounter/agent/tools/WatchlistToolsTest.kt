package com.beancounter.agent.tools

import com.beancounter.agent.client.WatchlistClient
import com.beancounter.agent.client.WatchlistEntry
import com.beancounter.client.services.ClassificationClient
import com.beancounter.common.contracts.AssetClassificationSummary
import com.beancounter.common.contracts.BulkClassificationResponse
import com.beancounter.common.contracts.PricePoint
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.time.LocalDate

/**
 * [WatchlistTools] hands the model each watched asset with its sector, the user's
 * entry and target, and momentum read from the asset's own price history.
 */
class WatchlistToolsTest {
    private val usd = Currency("USD")
    private val us = Market("US", "US", currencyId = "USD", currency = usd)
    private val today = LocalDate.parse("2026-10-16")
    private val dateUtils = mock<DateUtils> { on { date } doReturn today }

    private fun entry(
        code: String,
        price: String? = "120"
    ) = WatchlistEntry(
        asset = Asset(code = code, id = "id-$code", name = "$code Inc", market = us),
        currency = usd,
        addedPrice = BigDecimal("100"),
        addedOn = "2026-06-01",
        targetPrice = BigDecimal("150"),
        price = price?.let(::BigDecimal),
        priceDate = price?.let { "2026-10-16" },
        changeOnDayPercent = price?.let { BigDecimal("0.0125") },
        changeSinceAddedPercent = price?.let { BigDecimal("0.2") },
        toTarget = price?.let { BigDecimal("0.25") }
    )

    private val closes =
        (60 downTo 0).map { back ->
            PricePoint(priceDate = today.minusDays(back.toLong()), close = BigDecimal(180 - back))
        }

    private fun tools(
        vararg entries: WatchlistEntry,
        watchlistClient: WatchlistClient =
            mock {
                on { getWatchlist() } doReturn entries.toList()
                on { getCloses(any(), any()) } doReturn closes
            }
    ): WatchlistTools {
        val classificationClient =
            mock<ClassificationClient> {
                on { getClassifications(any()) } doReturn
                    BulkClassificationResponse(
                        mapOf("id-MSFT" to AssetClassificationSummary("id-MSFT", "Technology", "Software"))
                    )
            }
        return WatchlistTools(watchlistClient, classificationClient, dateUtils)
    }

    @Test
    fun `should describe each watched asset with its sector, entry, target and momentum`() {
        val brief = tools(entry("MSFT")).getWatchlist()

        assertThat(brief.count).isEqualTo(1)
        assertThat(brief.truncated).isFalse()
        val msft = brief.items.single()
        assertThat(msft.code).isEqualTo("MSFT")
        assertThat(msft.market).isEqualTo("US")
        assertThat(msft.name).isEqualTo("MSFT Inc")
        assertThat(msft.sector).isEqualTo("Technology")
        assertThat(msft.industry).isEqualTo("Software")
        assertThat(msft.currency).isEqualTo("USD")
        assertThat(msft.price).isEqualTo(120.0)
        assertThat(msft.addedPrice).isEqualTo(100.0)
        assertThat(msft.addedOn).isEqualTo("2026-06-01")
        assertThat(msft.targetPrice).isEqualTo(150.0)
        assertThat(msft.changeOnDay).isEqualTo(0.0125)
        assertThat(msft.changeSinceAdded).isEqualTo(0.2)
        assertThat(msft.toTarget).isEqualTo(0.25)
        assertThat(msft.momentum.asOf).isEqualTo("2026-10-16")
        assertThat(msft.momentum.return1m).isNotNull()
    }

    @Test
    fun `should read thirteen months of history so a twelve month return can be measured`() {
        val client =
            mock<WatchlistClient> {
                on { getWatchlist() } doReturn listOf(entry("MSFT"))
                on { getCloses("id-MSFT", "2025-09-16") } doReturn closes
            }

        val msft = tools(watchlistClient = client).getWatchlist().items.single()

        assertThat(msft.momentum.asOf).isEqualTo("2026-10-16")
    }

    @Test
    fun `should still describe an asset whose history cannot be read`() {
        val client =
            mock<WatchlistClient> {
                on { getWatchlist() } doReturn listOf(entry("MSFT"))
                on { getCloses(any(), any()) } doThrow RestClientException("down")
            }

        val msft = tools(watchlistClient = client).getWatchlist().items.single()

        assertThat(msft.code).isEqualTo("MSFT")
        assertThat(msft.sector).isEqualTo("Technology")
        assertThat(msft.momentum).isEqualTo(Momentum())
    }

    @Test
    fun `should leave price readings empty for an asset with no price`() {
        val fresh = tools(entry("NEW", price = null)).getWatchlist().items.single()

        assertThat(fresh.price).isNull()
        assertThat(fresh.changeOnDay).isNull()
        assertThat(fresh.toTarget).isNull()
        assertThat(fresh.sector).isNull()
    }

    @Test
    fun `should cap the brief and say so when the watchlist is long`() {
        val many = (1..WatchlistTools.MAX_ITEMS + 5).map { entry("A$it") }.toTypedArray()

        val brief = tools(*many).getWatchlist()

        assertThat(brief.items).hasSize(WatchlistTools.MAX_ITEMS)
        assertThat(brief.count).isEqualTo(WatchlistTools.MAX_ITEMS + 5)
        assertThat(brief.truncated).isTrue()
    }

    @Test
    fun `should not read any history for an empty watchlist`() {
        val client = mock<WatchlistClient> { on { getWatchlist() } doReturn emptyList() }

        val brief = tools(watchlistClient = client).getWatchlist()

        assertThat(brief.items).isEmpty()
        assertThat(brief.count).isZero()
        verify(client, never()).getCloses(any(), any())
    }
}