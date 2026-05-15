package com.beancounter.marketdata.metrics

import com.beancounter.marketdata.assets.AccountingTypeRepository
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.assets.PrivateAssetConfigRepository
import com.beancounter.marketdata.broker.BrokerSettlementAccountRepository
import com.beancounter.marketdata.portfolio.PortfolioRepository
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.providers.eodhd.news.NewsArticleRepo
import com.beancounter.marketdata.providers.eodhd.news.NewsFetchRepo
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.tax.TaxRateRepository
import com.beancounter.marketdata.trn.TrnRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

class EntityCountMetricsTest {
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var systemUserRepository: SystemUserRepository
    private lateinit var assetRepository: AssetRepository
    private lateinit var marketDataRepo: MarketDataRepo
    private lateinit var portfolioRepository: PortfolioRepository
    private lateinit var trnRepository: TrnRepository
    private lateinit var newsArticleRepo: NewsArticleRepo
    private lateinit var newsFetchRepo: NewsFetchRepo
    private lateinit var brokerSettlementAccountRepository: BrokerSettlementAccountRepository
    private lateinit var taxRateRepository: TaxRateRepository
    private lateinit var privateAssetConfigRepository: PrivateAssetConfigRepository
    private lateinit var accountingTypeRepository: AccountingTypeRepository
    private lateinit var metrics: EntityCountMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        systemUserRepository = mock { on { count() } doReturn 7L }
        assetRepository =
            mock {
                on { count() } doReturn 100L
                on { countByMarketCode() } doReturn
                    listOf(
                        MarketCount("NASDAQ", 60),
                        MarketCount("NYSE", 40)
                    )
            }
        marketDataRepo =
            mock {
                on { count() } doReturn 12_000L
                on { countByMarketCode() } doReturn listOf(MarketCount("NASDAQ", 12_000))
            }
        portfolioRepository = mock { on { count() } doReturn 5L }
        trnRepository =
            mock {
                on { count() } doReturn 250L
                on { countByTrnType() } doReturn
                    listOf(
                        TypeCount("BUY", 150),
                        TypeCount("SELL", 100)
                    )
            }
        newsArticleRepo =
            mock {
                on { count() } doReturn 80L
                on { countBySource() } doReturn listOf(SourceCount("EODHD", 80))
            }
        newsFetchRepo = mock { on { count() } doReturn 12L }
        brokerSettlementAccountRepository = mock { on { count() } doReturn 3L }
        taxRateRepository = mock { on { count() } doReturn 4L }
        privateAssetConfigRepository = mock { on { count() } doReturn 2L }
        accountingTypeRepository = mock { on { count() } doReturn 6L }

        metrics =
            EntityCountMetrics(
                meterRegistry = meterRegistry,
                systemUserRepository = systemUserRepository,
                assetRepository = assetRepository,
                marketDataRepo = marketDataRepo,
                portfolioRepository = portfolioRepository,
                trnRepository = trnRepository,
                newsArticleRepo = newsArticleRepo,
                newsFetchRepo = newsFetchRepo,
                brokerSettlementAccountRepository = brokerSettlementAccountRepository,
                taxRateRepository = taxRateRepository,
                privateAssetConfigRepository = privateAssetConfigRepository,
                accountingTypeRepository = accountingTypeRepository,
                breakdownRefreshMs = 300_000L
            )
        metrics.registerEntityGauges()
    }

    @Test
    fun `registers one gauge per entity tagged with entity name`() {
        val entities =
            listOf(
                "systemUser" to 7.0,
                "asset" to 100.0,
                "marketData" to 12_000.0,
                "portfolio" to 5.0,
                "transaction" to 250.0,
                "newsArticle" to 80.0,
                "newsFetch" to 12.0,
                "brokerSettlementAccount" to 3.0,
                "taxRate" to 4.0,
                "privateAssetConfig" to 2.0,
                "accountingType" to 6.0
            )

        entities.forEach { (entity, expected) ->
            val gauge = meterRegistry.find("beancounter.entity.count").tag("entity", entity).gauge()
            assertThat(gauge)
                .describedAs("gauge for entity=%s", entity)
                .isNotNull
            assertThat(gauge!!.value()).isEqualTo(expected)
        }
    }

    @Test
    fun `repository failure surfaces NaN instead of throwing`() {
        accountingTypeRepository.stub { on { count() } doThrow RuntimeException("db down") }

        val gauge = meterRegistry.find("beancounter.entity.count").tag("entity", "accountingType").gauge()
        assertThat(gauge).isNotNull
        assertThat(gauge!!.value()).isNaN
    }

    @Test
    fun `refreshBreakdowns populates assets-by-market multi-gauge`() {
        metrics.refreshBreakdowns()

        val nasdaq = meterRegistry.find("beancounter.asset.count.by_market").tag("market", "NASDAQ").gauge()
        val nyse = meterRegistry.find("beancounter.asset.count.by_market").tag("market", "NYSE").gauge()
        assertThat(nasdaq?.value()).isEqualTo(60.0)
        assertThat(nyse?.value()).isEqualTo(40.0)
    }

    @Test
    fun `refreshBreakdowns populates transactions-by-type multi-gauge`() {
        metrics.refreshBreakdowns()

        val buy = meterRegistry.find("beancounter.transaction.count.by_type").tag("type", "BUY").gauge()
        val sell = meterRegistry.find("beancounter.transaction.count.by_type").tag("type", "SELL").gauge()
        assertThat(buy?.value()).isEqualTo(150.0)
        assertThat(sell?.value()).isEqualTo(100.0)
    }

    @Test
    fun `refreshBreakdowns populates news-by-source multi-gauge`() {
        metrics.refreshBreakdowns()

        val eodhd = meterRegistry.find("beancounter.news.count.by_source").tag("source", "EODHD").gauge()
        assertThat(eodhd?.value()).isEqualTo(80.0)
    }

    @Test
    fun `breakdown refresh swallows repository failure for a single section`() {
        assetRepository.stub { on { countByMarketCode() } doThrow RuntimeException("group by timeout") }

        metrics.refreshBreakdowns()

        // failed section absent from registry, but other sections still register
        assertThat(
            meterRegistry.find("beancounter.asset.count.by_market").gauges()
        ).isEmpty()
        assertThat(
            meterRegistry.find("beancounter.transaction.count.by_type").tag("type", "BUY").gauge()
        ).isNotNull
    }
}