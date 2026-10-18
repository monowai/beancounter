package com.beancounter.agent.tools

import com.beancounter.agent.client.WatchlistClient
import com.beancounter.agent.client.WatchlistEntry
import com.beancounter.client.services.ClassificationClient
import com.beancounter.common.contracts.AssetClassificationSummary
import com.beancounter.common.contracts.BulkClassificationResponse
import com.beancounter.common.contracts.PricePoint
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetFundamentals
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
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
        price: String? = "120",
        currency: Currency = usd
    ) = WatchlistEntry(
        asset = Asset(code = code, id = "id-$code", name = "$code Inc", market = us),
        currency = currency,
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

    private fun fundamentals(eps: String = "4") =
        AssetFundamentals(
            assetId = "id-MSFT",
            fiscalYearEnd = LocalDate.parse("2026-06-30"),
            fiscalYear = 2026,
            epsDiluted = BigDecimal(eps),
            revenue = BigDecimal("100000000"),
            netIncome = BigDecimal("25000000"),
            dividendsPerShare = BigDecimal("1.20"),
            sharesOutstanding = 1_000_000L,
            asOf = LocalDate.parse("2026-10-12")
        )

    private fun withFundamentals(
        entry: WatchlistEntry,
        snapshot: AssetFundamentals?
    ) = mock<WatchlistClient> {
        on { getWatchlist() } doReturn listOf(entry)
        on { getCloses(any(), any()) } doReturn closes
        on { getFundamentals(any()) } doReturn snapshot
    }

    private fun tools(
        vararg entries: WatchlistEntry,
        watchlistClient: WatchlistClient =
            mock {
                on { getWatchlist() } doReturn entries.toList()
                on { getCloses(any(), any()) } doReturn closes
            },
        classificationClient: ClassificationClient =
            mock {
                on { getClassifications(any()) } doReturn
                    BulkClassificationResponse(
                        mapOf("id-MSFT" to AssetClassificationSummary("id-MSFT", "Technology", "Software"))
                    )
            },
        nanoTime: () -> Long = { 0L }
    ): WatchlistTools = WatchlistTools(watchlistClient, classificationClient, dateUtils, nanoTime)

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
    fun `should still describe an asset whose history read fails at svc-data`() {
        // svc-data's HTTP errors arrive as mapped exceptions, not RestClientException.
        val client =
            mock<WatchlistClient> {
                on { getWatchlist() } doReturn listOf(entry("MSFT"))
                on { getCloses(any(), any()) } doThrow SystemException("backfill failed")
            }

        val msft = tools(watchlistClient = client).getWatchlist().items.single()

        assertThat(msft.momentum).isEqualTo(Momentum())
    }

    @Test
    fun `should still describe the watchlist when classifications cannot be read`() {
        val classifications =
            mock<ClassificationClient> { on { getClassifications(any()) } doThrow BusinessException("bad") }

        val msft = tools(entry("MSFT"), classificationClient = classifications).getWatchlist().items.single()

        assertThat(msft.sector).isNull()
        assertThat(msft.momentum.asOf).isEqualTo("2026-10-16")
    }

    @Test
    fun `should stop reading history and fundamentals once the time budget is spent`() {
        // Each read takes 20 seconds, so only the reads that start inside the budget run.
        var now = 0L
        val client =
            mock<WatchlistClient> {
                on { getWatchlist() } doReturn (1..6).map { entry("A$it") }
                on { getCloses(any(), any()) } doAnswer {
                    now += 20_000_000_000L
                    closes
                }
                on { getFundamentals(any()) } doReturn fundamentals()
            }

        val brief = tools(watchlistClient = client, nanoTime = { now }).getWatchlist()

        assertThat(brief.items).hasSize(6)
        assertThat(brief.items.take(3).map { it.momentum.asOf }).containsOnly("2026-10-16")
        assertThat(brief.items.drop(3).map { it.momentum }).containsOnly(Momentum())
        assertThat(brief.items.take(3).map { it.fundamentals }).doesNotContainNull()
        assertThat(brief.items.drop(3).map { it.fundamentals }).containsOnlyNulls()
        assertThat(brief.momentumSkipped).isEqualTo(3)
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

    @Test
    fun `should attach the SEC fundamentals with valuation measured at the latest price`() {
        val msft =
            tools(
                watchlistClient = withFundamentals(entry("MSFT"), fundamentals())
            ).getWatchlist().items.single()

        val f = msft.fundamentals
        assertThat(f).isNotNull
        assertThat(f!!.source).isEqualTo("SEC")
        assertThat(f.fiscalYear).isEqualTo(2026)
        assertThat(f.fiscalYearEnd).isEqualTo("2026-06-30")
        assertThat(f.epsDiluted).isEqualTo(4.0)
        assertThat(f.revenue).isEqualTo(100_000_000.0)
        assertThat(f.netIncome).isEqualTo(25_000_000.0)
        assertThat(f.dividendsPerShare).isEqualTo(1.2)
        assertThat(f.sharesOutstanding).isEqualTo(1_000_000L)
        assertThat(f.priceToEarnings).isEqualTo(30.0)
        assertThat(f.dividendYield).isEqualTo(0.01)
        assertThat(f.marketCap).isEqualTo(120_000_000.0)
        assertThat(f.netMargin).isEqualTo(0.25)
    }

    @Test
    fun `should give no price to earnings for a loss making company`() {
        val msft =
            tools(watchlistClient = withFundamentals(entry("MSFT"), fundamentals(eps = "-2")))
                .getWatchlist()
                .items
                .single()

        assertThat(msft.fundamentals!!.epsDiluted).isEqualTo(-2.0)
        assertThat(msft.fundamentals!!.priceToEarnings).isNull()
        assertThat(msft.fundamentals!!.marketCap).isEqualTo(120_000_000.0)
    }

    @Test
    fun `should not measure USD filings against a price in another currency`() {
        val aud = Currency("AUD")
        val listed = entry("MSFT", currency = aud)

        val f =
            tools(
                watchlistClient = withFundamentals(listed, fundamentals())
            ).getWatchlist().items.single().fundamentals

        assertThat(f!!.epsDiluted).isEqualTo(4.0)
        assertThat(f.netMargin).isEqualTo(0.25)
        assertThat(f.priceToEarnings).isNull()
        assertThat(f.dividendYield).isNull()
        assertThat(f.marketCap).isNull()
    }

    @Test
    fun `should leave valuation empty for an asset with no price`() {
        val f =
            tools(watchlistClient = withFundamentals(entry("MSFT", price = null), fundamentals()))
                .getWatchlist()
                .items
                .single()
                .fundamentals

        assertThat(f!!.epsDiluted).isEqualTo(4.0)
        assertThat(f.priceToEarnings).isNull()
        assertThat(f.marketCap).isNull()
    }

    @Test
    fun `should leave fundamentals empty when svc-data has no snapshot`() {
        val msft = tools(watchlistClient = withFundamentals(entry("MSFT"), null)).getWatchlist().items.single()

        assertThat(msft.fundamentals).isNull()
        assertThat(msft.momentum.asOf).isEqualTo("2026-10-16")
    }

    @Test
    fun `should still describe an asset whose fundamentals read fails`() {
        val client =
            mock<WatchlistClient> {
                on { getWatchlist() } doReturn listOf(entry("MSFT"))
                on { getCloses(any(), any()) } doReturn closes
                on { getFundamentals(any()) } doThrow SystemException("down")
            }

        val msft = tools(watchlistClient = client).getWatchlist().items.single()

        assertThat(msft.fundamentals).isNull()
        assertThat(msft.sector).isEqualTo("Technology")
    }
}