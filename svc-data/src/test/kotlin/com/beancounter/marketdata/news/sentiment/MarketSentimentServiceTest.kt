package com.beancounter.marketdata.news.sentiment

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.news.NewsArticle
import com.beancounter.marketdata.news.NewsArticleRepo
import com.beancounter.marketdata.news.NewsArticleTicker
import com.beancounter.marketdata.news.NewsServiceFacade
import com.beancounter.marketdata.news.eodhd.EodhdNewsProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate

/**
 * [MarketSentimentService] against real H2 state: stored articles in, daily rows out. Only the
 * news download ([NewsServiceFacade]) is mocked; the grouped count and the upsert run for real,
 * and every spec asserts on the rows or the served series.
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db")
@AutoConfigureMockAuth
internal class MarketSentimentServiceTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var newsArticleRepo: NewsArticleRepo

    @Autowired
    private lateinit var repo: MarketSentimentDailyRepository

    @Autowired
    private lateinit var dateUtils: DateUtils

    private val newsServiceFacade = mock<NewsServiceFacade>()
    private lateinit var service: MarketSentimentService

    private val yesterday: LocalDate get() = dateUtils.date.minusDays(1)

    @BeforeEach
    fun setUp() {
        repo.deleteAll()
        newsArticleRepo.deleteAll()
        service =
            MarketSentimentService(
                newsServiceFacade,
                newsArticleRepo,
                repo,
                EodhdNewsProperties(retentionDays = RETENTION_DAYS),
                dateUtils
            )
    }

    private fun article(
        polarity: String,
        day: LocalDate,
        vararg keys: String
    ) {
        val published = day.atTime(NOON, 0)
        newsArticleRepo.save(
            NewsArticle(
                externalId = "mkt-${sequence++}",
                published = published,
                title = "headline",
                polarity = BigDecimal(polarity),
                fetchedAt = published,
                tickerLinks = keys.map { NewsArticleTicker(it) }.toMutableSet()
            )
        )
    }

    private fun series(
        kind: SentimentScopeKind,
        name: String
    ): MarketSentimentSeries? = service.get(RETENTION_DAYS.toInt()).firstOrNull { it.kind == kind && it.name == name }

    @Test
    fun `rollup counts positive, neutral and negative articles for a topic on one day`() {
        article("0.9", yesterday, INFLATION)
        article("0.35", yesterday, INFLATION)
        article("0.1", yesterday, INFLATION)
        article("-0.34", yesterday, INFLATION)
        article("-0.35", yesterday, INFLATION)

        service.rollup()

        val point = series(SentimentScopeKind.TOPIC, "inflation")!!.points.single()
        assertThat(point.date).isEqualTo(yesterday)
        assertThat(point.positive).isEqualTo(2)
        assertThat(point.neutral).isEqualTo(2)
        assertThat(point.negative).isEqualTo(1)
        assertThat(point.polarity).isEqualByComparingTo(BigDecimal("0.1320"))
    }

    @Test
    fun `rollup keeps each topic and each sector in its own series`() {
        article("0.9", yesterday, INFLATION)
        article("-0.9", yesterday, ENERGY_TOPIC)
        article("-0.8", yesterday, TECH_ETF)

        service.rollup()

        assertThat(service.get(RETENTION_DAYS.toInt()).map { it.kind to it.name })
            .containsExactly(
                SentimentScopeKind.TOPIC to "energy",
                SentimentScopeKind.TOPIC to "inflation",
                SentimentScopeKind.SECTOR to "technology"
            )
        assertThat(series(SentimentScopeKind.TOPIC, "inflation")!!.points.single().positive).isEqualTo(1)
        assertThat(series(SentimentScopeKind.TOPIC, "energy")!!.points.single().negative).isEqualTo(1)
        assertThat(series(SentimentScopeKind.SECTOR, "technology")!!.points.single().negative).isEqualTo(1)
    }

    @Test
    fun `an article filed under two scopes counts in both and holding news counts in neither`() {
        article("0.9", yesterday, INFLATION, ENERGY_TOPIC, "AAPL.US")
        article("0.9", yesterday, "AAPL.US")

        service.rollup()

        assertThat(repo.findAll()).hasSize(2)
        assertThat(series(SentimentScopeKind.TOPIC, "inflation")!!.points.single().positive).isEqualTo(1)
        assertThat(series(SentimentScopeKind.TOPIC, "energy")!!.points.single().positive).isEqualTo(1)
    }

    @Test
    fun `rollup gives each day its own point, oldest first`() {
        article("0.9", yesterday, INFLATION)
        article("-0.9", yesterday.minusDays(1), INFLATION)

        service.rollup()

        val points = series(SentimentScopeKind.TOPIC, "inflation")!!.points
        assertThat(points.map { it.date }).containsExactly(yesterday.minusDays(1), yesterday)
        assertThat(points.map { it.negative }).containsExactly(1, 0)
    }

    @Test
    fun `a second rollup updates the day in place when a late article arrives`() {
        article("0.9", yesterday, INFLATION)
        assertThat(service.rollup()).isEqualTo(1)
        assertThat(service.rollup()).isZero()

        article("-0.9", yesterday, INFLATION)
        assertThat(service.rollup()).isEqualTo(1)

        assertThat(repo.findAll()).hasSize(1)
        val point = series(SentimentScopeKind.TOPIC, "inflation")!!.points.single()
        assertThat(point.positive).isEqualTo(1)
        assertThat(point.negative).isEqualTo(1)
    }

    @Test
    fun `a stored day survives once its articles are pruned`() {
        article("0.9", yesterday, INFLATION)
        service.rollup()
        newsArticleRepo.deleteAll()

        service.rollup()

        assertThat(series(SentimentScopeKind.TOPIC, "inflation")!!.points.single().positive).isEqualTo(1)
    }

    @Test
    fun `rollup leaves out the partly pruned days at the end of article retention`() {
        val boundary = dateUtils.date.minusDays(RETENTION_DAYS - 1)
        article("0.9", boundary, INFLATION)

        service.rollup()

        assertThat(repo.findAll()).isEmpty()
    }

    @Test
    fun `get returns only the trailing window`() {
        article("0.9", yesterday, INFLATION)
        article("0.9", yesterday.minusDays(10), INFLATION)
        service.rollup()

        assertThat(
            service
                .get(5)
                .single()
                .points
                .map { it.date }
        ).containsExactly(yesterday)
    }

    @Test
    fun `refresh downloads topic and sector news then rolls up`() {
        article("0.9", yesterday, TECH_ETF)

        val result = service.refresh()

        verify(newsServiceFacade).getTopicNews(MarketSentimentScopes.TOPICS)
        verify(newsServiceFacade).getMarketNews(MarketSentimentScopes.SECTORS.values.toList(), null)
        assertThat(result.rows).isEqualTo(1)
        assertThat(result.scopes).isEqualTo(MarketSentimentScopes.ALL.size)
    }

    @Test
    fun `refresh still rolls up stored articles when the news download fails`() {
        whenever(newsServiceFacade.getTopicNews(any())).thenThrow(IllegalStateException("provider down"))
        whenever(newsServiceFacade.getMarketNews(any(), anyOrNull())).thenThrow(IllegalStateException("provider down"))
        article("0.9", yesterday, INFLATION)

        assertThat(service.refresh().rows).isEqualTo(1)
    }

    private companion object {
        const val RETENTION_DAYS = 30L
        const val NOON = 12
        const val INFLATION = "TOPIC:INFLATION"
        const val ENERGY_TOPIC = "TOPIC:ENERGY"
        const val TECH_ETF = "XLK.US"
        var sequence = 0
    }
}