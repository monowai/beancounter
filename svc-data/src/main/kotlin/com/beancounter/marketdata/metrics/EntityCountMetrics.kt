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
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.CrudRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Exposes svc-data persistence counts as Micrometer gauges so they can be
 * scraped by Prometheus or rendered by Spring Boot Admin.
 *
 * Two flavours of metric:
 *  - `beancounter.entity.count{entity=…}` — single per-entity total. Gauge supplier
 *    runs `repo.count()` on every actuator scrape. Suppliers are wrapped in
 *    [safeCount] so a failing repo returns NaN (rendered as `—`) instead of
 *    breaking the whole `/actuator/metrics` response.
 *  - `beancounter.<entity>.count.by_<dim>{<dim>=…}` — grouped breakdown. Backed by
 *    a [MultiGauge] refreshed on a [Scheduled] tick, not on scrape, because
 *    `GROUP BY` on large tables is too expensive to run every 10s.
 */
@Component
class EntityCountMetrics(
    private val meterRegistry: MeterRegistry,
    private val systemUserRepository: SystemUserRepository,
    private val assetRepository: AssetRepository,
    private val marketDataRepo: MarketDataRepo,
    private val portfolioRepository: PortfolioRepository,
    private val trnRepository: TrnRepository,
    private val newsArticleRepo: NewsArticleRepo,
    private val newsFetchRepo: NewsFetchRepo,
    private val brokerSettlementAccountRepository: BrokerSettlementAccountRepository,
    private val taxRateRepository: TaxRateRepository,
    private val privateAssetConfigRepository: PrivateAssetConfigRepository,
    private val accountingTypeRepository: AccountingTypeRepository,
    @Value("\${beancounter.metrics.entity-count.refresh-ms:300000}")
    private val breakdownRefreshMs: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val assetsByMarket: MultiGauge =
        MultiGauge
            .builder("beancounter.asset.count.by_market")
            .description("Asset row count grouped by marketCode")
            .register(meterRegistry)

    private val marketDataByMarket: MultiGauge =
        MultiGauge
            .builder("beancounter.market_data.count.by_market")
            .description("MarketData row count grouped by owning asset's marketCode")
            .register(meterRegistry)

    private val transactionsByType: MultiGauge =
        MultiGauge
            .builder("beancounter.transaction.count.by_type")
            .description("Transaction row count grouped by trnType")
            .register(meterRegistry)

    private val newsBySource: MultiGauge =
        MultiGauge
            .builder("beancounter.news.count.by_source")
            .description("NewsArticle row count grouped by source")
            .register(meterRegistry)

    @PostConstruct
    fun registerEntityGauges() {
        registerCount("systemUser", systemUserRepository)
        registerCount("asset", assetRepository)
        registerCount("marketData", marketDataRepo)
        registerCount("portfolio", portfolioRepository)
        registerCount("transaction", trnRepository)
        registerCount("newsArticle", newsArticleRepo)
        registerCount("newsFetch", newsFetchRepo)
        registerCount("brokerSettlementAccount", brokerSettlementAccountRepository)
        registerCount("taxRate", taxRateRepository)
        registerCount("privateAssetConfig", privateAssetConfigRepository)
        registerCount("accountingType", accountingTypeRepository)
    }

    /**
     * Refresh breakdown gauges on a fixed delay. Configured via
     * `beancounter.metrics.entity-count.refresh-ms` (default 5 minutes).
     * Uses fixedDelay so a slow query doesn't queue overlapping runs.
     */
    @Scheduled(fixedDelayString = "\${beancounter.metrics.entity-count.refresh-ms:300000}")
    fun refreshBreakdowns() {
        refresh("assetsByMarket", assetsByMarket, "market") {
            assetRepository.countByMarketCode().map { it.market to it.count }
        }
        refresh("marketDataByMarket", marketDataByMarket, "market") {
            marketDataRepo.countByMarketCode().map { it.market to it.count }
        }
        refresh("transactionsByType", transactionsByType, "type") {
            trnRepository.countByTrnType().map { it.type to it.count }
        }
        refresh("newsBySource", newsBySource, "source") {
            newsArticleRepo.countBySource().map { it.source to it.count }
        }
    }

    private fun registerCount(
        entity: String,
        repository: CrudRepository<*, *>
    ) {
        Gauge
            .builder("beancounter.entity.count") { safeCount(entity, repository) }
            .tag("entity", entity)
            .description("Total $entity rows")
            .strongReference(true)
            .register(meterRegistry)
    }

    private fun safeCount(
        entity: String,
        repository: CrudRepository<*, *>
    ): Double =
        try {
            repository.count().toDouble()
        } catch (ex: Exception) {
            log.warn("entity count gauge failed for {}: {}", entity, ex.message)
            Double.NaN
        }

    private fun refresh(
        label: String,
        multi: MultiGauge,
        tagKey: String,
        supplier: () -> List<Pair<String, Long>>
    ) {
        try {
            val rows =
                supplier().map { (key, value) ->
                    MultiGauge.Row.of(Tags.of(Tag.of(tagKey, key)), value.toDouble())
                }
            multi.register(rows, true)
        } catch (ex: Exception) {
            log.warn("breakdown gauge refresh failed for {}: {}", label, ex.message)
        }
    }
}