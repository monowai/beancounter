package com.beancounter.marketdata.news

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.news.eodhd.EodhdNewsProperties
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdProxy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * [NewsSentimentService] against real H2 state. Only the EODHD HTTP seam ([EodhdProxy]) is mocked;
 * asset selection, symbol routing (`eodhd` profile: NASDAQ → `.US`) and the upsert all run for real,
 * and every spec asserts on [NewsSentimentDailyRepository] rows rather than on mock interactions.
 *
 * Batch size is pinned to 1 so each test asset maps to exactly one `/api/sentiments` call and can be
 * stubbed independently of whatever other NASDAQ assets sibling test classes left in the shared H2.
 *
 * The `eodhd` profile points the gateway at `wiremock.server.port`; the HTTP seam is mocked here, so
 * the placeholder only has to resolve.
 */
@SpringBootTest(classes = [MarketDataBoot::class], properties = ["wiremock.server.port=0"])
@ActiveProfiles("h2db", "eodhd")
@AutoConfigureMockAuth
internal class NewsSentimentServiceTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var eodhdProxy: EodhdProxy

    @Autowired
    private lateinit var eodhdConfig: EodhdConfig

    @Autowired
    private lateinit var assetFinder: AssetFinder

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var repo: NewsSentimentDailyRepository

    @Autowired
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var marketService: MarketService

    private lateinit var service: NewsSentimentService

    private val nasdaq = Market("NASDAQ")

    // A real lower bound: LocalDate.MIN is outside what H2 can bind as a DATE parameter.
    private val epoch: LocalDate = LocalDate.parse("2000-01-01")
    private val coveredId = "snt-covered"
    private val absentId = "snt-absent"
    private val failingId = "snt-failing"

    @BeforeEach
    fun setUp() {
        repo.deleteAll()
        listOf("SNTA" to coveredId, "SNTB" to absentId, "SNTF" to failingId).forEach { (code, id) ->
            assetRepository.save(Asset(code = code, id = id, name = code, market = nasdaq, status = Status.Active))
        }
        service =
            NewsSentimentService(
                eodhdProxy,
                eodhdConfig,
                assetFinder,
                repo,
                EodhdNewsProperties(sentimentBatchSize = 1, sentimentInitialDays = 30),
                dateUtils
            )
        // Every other active NASDAQ/NYSE asset in the shared H2 gets "no coverage" from EODHD.
        whenever(eodhdProxy.getSentiments(any(), any(), any())).thenReturn("{}")
    }

    // Five decimals on purpose: the column is NUMERIC(6,4), so the parser must round before the
    // stored-vs-incoming comparison or every run rewrites every row.
    private fun stubCovered(
        count: Int = 27,
        normalized: String = "0.89371"
    ) {
        whenever(eodhdProxy.getSentiments(eq("SNTA.US"), any(), any()))
            .thenReturn(
                """
                {"SNTA.US":[{"date":"2026-10-06","count":$count,"normalized":$normalized},
                            {"date":"2026-10-05","count":25,"normalized":0.807}],
                 "TSCO.LSE":[{"date":"2026-10-05","count":3,"normalized":0.5133}]}
                """.trimIndent()
            )
    }

    @Test
    fun `refresh persists one row per returned date for a covered asset`() {
        stubCovered()

        val result = service.refresh()

        val rows = repo.findByAssetIdInAndPriceDateGreaterThanEqualOrderByPriceDateAsc(listOf(coveredId), epoch)
        assertThat(rows).hasSize(2)
        assertThat(rows.last().priceDate).isEqualTo(LocalDate.parse("2026-10-06"))
        assertThat(rows.last().articleCount).isEqualTo(27)
        assertThat(rows.last().normalized).isEqualByComparingTo(BigDecimal("0.8937"))
        assertThat(rows.last().symbol).isEqualTo("SNTA.US")
        assertThat(result.rows).isGreaterThanOrEqualTo(2)
        assertThat(result.calls).isGreaterThanOrEqualTo(3)
        assertThat(result.assets).isGreaterThanOrEqualTo(3)
    }

    @Test
    fun `refresh ignores symbols that were not requested and assets EODHD did not answer for`() {
        stubCovered()

        service.refresh()

        assertThat(repo.findAll().map { it.assetId }).doesNotContain(absentId)
        assertThat(repo.findAll().map { it.symbol }).doesNotContain("TSCO.LSE")
    }

    @Test
    fun `refresh persists rows when EODHD echoes the symbol in a different case`() {
        whenever(eodhdProxy.getSentiments(eq("SNTA.US"), any(), any()))
            .thenReturn("""{"snta.us":[{"date":"2026-10-06","count":9,"normalized":0.25}]}""")

        service.refresh()

        val rows = repo.findByAssetIdInAndPriceDateGreaterThanEqualOrderByPriceDateAsc(listOf(coveredId), epoch)
        assertThat(rows).hasSize(1)
        assertThat(rows.single().articleCount).isEqualTo(9)
        assertThat(rows.single().symbol).isEqualTo("SNTA.US")
    }

    @Test
    fun `refresh overwrites same-day counts on re-run instead of duplicating`() {
        stubCovered(count = 27)
        service.refresh()
        stubCovered(count = 30, normalized = "0.75")

        service.refresh()

        val rows =
            repo
                .findByAssetIdInAndPriceDateGreaterThanEqualOrderByPriceDateAsc(listOf(coveredId), epoch)
                .filter { it.priceDate == LocalDate.parse("2026-10-06") }
        assertThat(rows).hasSize(1)
        assertThat(rows.single().articleCount).isEqualTo(30)
        assertThat(rows.single().normalized).isEqualByComparingTo(BigDecimal("0.75"))
    }

    @Test
    fun `refresh skips a batch that throws and still persists the others`() {
        stubCovered()
        whenever(eodhdProxy.getSentiments(eq("SNTF.US"), any(), any()))
            .thenThrow(IllegalStateException("EODHD 502"))

        val result = service.refresh()

        assertThat(repo.findByAssetIdInAndPriceDateGreaterThanEqualOrderByPriceDateAsc(listOf(coveredId), epoch))
            .hasSize(2)
        assertThat(result.failedBatches).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `refresh counts an empty provider body as a failed batch with no rows`() {
        whenever(eodhdProxy.getSentiments(eq("SNTA.US"), any(), any())).thenReturn("")

        val result = service.refresh()

        assertThat(result.failedBatches).isEqualTo(1)
        assertThat(repo.findByAssetIdInAndPriceDateGreaterThanEqualOrderByPriceDateAsc(listOf(coveredId), epoch))
            .isEmpty()
    }

    @Test
    fun `refresh requests from latest stored date minus two days when history exists`() {
        val latest = dateUtils.date.minusDays(5)
        repo.save(
            NewsSentimentDaily(
                assetId = coveredId,
                priceDate = latest,
                symbol = "SNTA.US",
                articleCount = 1,
                normalized = BigDecimal("0.1"),
                fetchedAt = LocalDateTime.now(dateUtils.zoneId)
            )
        )

        service.refresh()

        val from = argumentCaptor<String>()
        verify(eodhdProxy).getSentiments(eq("SNTA.US"), from.capture(), any())
        assertThat(from.firstValue).isEqualTo(latest.minusDays(2).toString())
    }

    @Test
    fun `refresh requests the initial window when nothing is stored for the asset`() {
        service.refresh()

        val from = argumentCaptor<String>()
        verify(eodhdProxy).getSentiments(eq("SNTA.US"), from.capture(), any())
        assertThat(from.firstValue).isEqualTo(dateUtils.date.minusDays(30).toString())
    }

    @Test
    fun `refresh is single-flight and a concurrent call returns skipped`() {
        val firstCallStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(eodhdProxy.getSentiments(any(), any(), any())).thenAnswer {
            firstCallStarted.countDown()
            release.await(5, TimeUnit.SECONDS)
            "{}"
        }
        val worker = thread { service.refresh() }
        assertThat(firstCallStarted.await(5, TimeUnit.SECONDS)).isTrue()

        val concurrent = service.refresh()

        release.countDown()
        worker.join(10_000)
        assertThat(concurrent.skipped).isTrue()
        assertThat(concurrent.calls).isZero()
        assertThat(service.refresh().skipped).isFalse()
    }

    @Test
    fun `refresh batches new assets apart from assets with history`() {
        val latest = dateUtils.date.minusDays(5)
        val known = (1..20).map { "snt-known-$it" }
        known.forEachIndexed { index, id ->
            assetRepository.save(
                Asset(code = "SNTK$index", id = id, name = id, market = nasdaq, status = Status.Active)
            )
        }
        (known + coveredId).forEach { id ->
            repo.save(
                NewsSentimentDaily(id, latest, "$id.US", 1, BigDecimal("0.1"), LocalDateTime.now(dateUtils.zoneId))
            )
        }
        val wide =
            NewsSentimentService(
                eodhdProxy,
                eodhdConfig,
                assetFinder,
                repo,
                EodhdNewsProperties(sentimentBatchSize = 25, sentimentInitialDays = 30),
                dateUtils
            )

        wide.refresh()

        val symbols = argumentCaptor<String>()
        val from = argumentCaptor<String>()
        verify(eodhdProxy, atLeastOnce()).getSentiments(symbols.capture(), from.capture(), any())
        val calls = symbols.allValues.zip(from.allValues)
        val knownCall = calls.single { it.first.contains("SNTA.US") }
        assertThat(knownCall.first).doesNotContain("SNTB.US")
        assertThat(knownCall.second).isEqualTo(latest.minusDays(2).toString())
        val freshCall = calls.single { it.first.contains("SNTB.US") }
        assertThat(freshCall.second).isEqualTo(dateUtils.date.minusDays(30).toString())
    }

    @Test
    fun `refresh leaves unchanged rows untouched on an identical re-run`() {
        stubCovered()
        service.refresh()
        val before =
            repo
                .findByAssetIdInAndPriceDateGreaterThanEqualOrderByPriceDateAsc(listOf(coveredId), epoch)
                .associate { it.priceDate to it.fetchedAt }

        val second = service.refresh()

        assertThat(second.rows).isZero()
        val after =
            repo
                .findByAssetIdInAndPriceDateGreaterThanEqualOrderByPriceDateAsc(listOf(coveredId), epoch)
                .associate { it.priceDate to it.fetchedAt }
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun `refresh with no EODHD-enabled market makes no provider call`() {
        val noMarkets =
            EodhdConfig(marketService).also {
                ReflectionTestUtils.setField(it, "apiKey", "demo")
                ReflectionTestUtils.setField(it, "markets", "")
            }
        val idle =
            NewsSentimentService(eodhdProxy, noMarkets, assetFinder, repo, EodhdNewsProperties(), dateUtils)

        val result = idle.refresh()

        assertThat(result).isEqualTo(SentimentRefreshResult(assets = 0, calls = 0, rows = 0))
        verify(eodhdProxy, never()).getSentiments(any(), any(), any())
    }

    @Test
    fun `get returns stored points per asset inside the window ordered by date`() {
        val today = dateUtils.date
        listOf(1L, 3L, 40L).forEach { daysAgo ->
            repo.save(
                NewsSentimentDaily(
                    assetId = coveredId,
                    priceDate = today.minusDays(daysAgo),
                    symbol = "SNTA.US",
                    articleCount = daysAgo.toInt(),
                    normalized = BigDecimal("0.5"),
                    fetchedAt = LocalDateTime.now(dateUtils.zoneId)
                )
            )
        }

        val points = service.get(listOf(coveredId, absentId), 30)

        assertThat(points).containsOnlyKeys(coveredId)
        assertThat(points.getValue(coveredId).map { it.date })
            .containsExactly(today.minusDays(3), today.minusDays(1))
        assertThat(points.getValue(coveredId).map { it.count }).containsExactly(3, 1)
    }
}