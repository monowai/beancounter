package com.beancounter.marketdata.news

import com.beancounter.common.model.Asset
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.news.eodhd.EodhdNewsProperties
import com.beancounter.marketdata.news.eodhd.EodhdSentimentParser
import com.beancounter.marketdata.news.eodhd.SentimentPoint
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdProxy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

/** Counts from one [NewsSentimentService.refresh] run. */
data class SentimentRefreshResult(
    val assets: Int,
    val calls: Int,
    val rows: Int,
    val failedBatches: Int = 0
)

/**
 * Daily aggregated news sentiment per asset, backed by EODHD's `/api/sentiments` feed and cached
 * in [NewsSentimentDaily].
 *
 * [refresh] fans out over every active asset on an EODHD-enabled market (`eodhd.markets`
 * allowlist), [EodhdNewsProperties.sentimentBatchSize] symbols per call. Each batch asks from the
 * *earliest* "latest stored day minus [OVERLAP_DAYS]" across its assets — so a brand-new asset in
 * the batch gets the full [EodhdNewsProperties.sentimentInitialDays] window and assets with history
 * get the trailing days re-read and overwritten (EODHD keeps counting articles for a day after it
 * first reports it). One failed batch is logged and skipped; the others still persist.
 *
 * [get] never touches EODHD — it is a DB read, so the UI/agent cost is zero provider quota.
 */
@Service
class NewsSentimentService(
    private val eodhdProxy: EodhdProxy,
    private val eodhdConfig: EodhdConfig,
    private val assetFinder: AssetFinder,
    private val repo: NewsSentimentDailyRepository,
    private val newsProperties: EodhdNewsProperties,
    private val dateUtils: DateUtils = DateUtils()
) {
    fun refresh(): SentimentRefreshResult {
        val assets =
            assetFinder
                .findActiveAssetsForPricing()
                .filter { eodhdConfig.supportsMarketCode(eodhdConfig.markets, it.marketCode) }
        val today = dateUtils.date
        var calls = 0
        var rows = 0
        var failed = 0
        assets.chunked(newsProperties.sentimentBatchSize).forEach { batch ->
            val bySymbol = batch.groupBy { eodhdConfig.getPriceCode(it) }
            val from = batch.minOf { fromDate(it.id, today) }
            val symbols = bySymbol.keys.joinToString(",")
            calls++
            val parsed =
                runCatching {
                    EodhdSentimentParser.parse(eodhdProxy.getSentiments(symbols, from.toString(), eodhdConfig.apiKey))
                }.getOrElse {
                    failed++
                    log.warn("Sentiment batch failed for [{}] from {}: {}", symbols, from, it.message)
                    return@forEach
                }
            rows += upsert(bySymbol, parsed, from)
        }
        val result = SentimentRefreshResult(assets.size, calls, rows, failed)
        log.info("Sentiment refresh: {}", result)
        return result
    }

    /** Stored points per asset id for the trailing [days], oldest first. Assets with no rows are absent. */
    fun get(
        assetIds: List<String>,
        days: Int
    ): Map<String, List<SentimentPoint>> {
        if (assetIds.isEmpty()) {
            return emptyMap()
        }
        val from = dateUtils.date.minusDays(days.toLong())
        return repo
            .findByAssetIdInAndPriceDateGreaterThanEqualOrderByPriceDateAsc(assetIds, from)
            .groupBy({ it.assetId }, { SentimentPoint(it.priceDate, it.articleCount, it.normalized) })
    }

    private fun fromDate(
        assetId: String,
        today: LocalDate
    ): LocalDate {
        val latest = repo.findTopByAssetIdOrderByPriceDateDesc(assetId)
        return if (latest == null) {
            today.minusDays(newsProperties.sentimentInitialDays)
        } else {
            latest.priceDate.minusDays(OVERLAP_DAYS)
        }
    }

    private fun upsert(
        bySymbol: Map<String, List<Asset>>,
        parsed: Map<String, List<SentimentPoint>>,
        from: LocalDate
    ): Int {
        val assetIds = bySymbol.values.flatten().map { it.id }
        val existing =
            repo
                .findByAssetIdInAndPriceDateGreaterThanEqualOrderByPriceDateAsc(assetIds, from)
                .associateBy { it.assetId to it.priceDate }
                .toMutableMap()
        val fetchedAt = LocalDateTime.now(dateUtils.zoneId)
        val toSave = linkedSetOf<NewsSentimentDaily>()
        parsed.forEach { (symbol, points) ->
            // Symbols we did not ask for are ignored; symbols EODHD did not answer for never appear.
            bySymbol[symbol].orEmpty().forEach { asset ->
                points.filter { it.date >= from }.forEach { point ->
                    val row =
                        existing.getOrPut(asset.id to point.date) {
                            NewsSentimentDaily(assetId = asset.id, priceDate = point.date)
                        }
                    row.symbol = symbol
                    row.articleCount = point.count
                    row.normalized = point.normalized
                    row.fetchedAt = fetchedAt
                    toSave += row
                }
            }
        }
        repo.saveAll(toSave)
        return toSave.size
    }

    companion object {
        private val log = LoggerFactory.getLogger(NewsSentimentService::class.java)

        /** Trailing days re-read on every run so late-arriving article counts overwrite the stored day. */
        const val OVERLAP_DAYS = 2L
    }
}