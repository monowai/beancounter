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
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock

/** Counts from one [NewsSentimentService.refresh] run. */
data class SentimentRefreshResult(
    val assets: Int,
    val calls: Int,
    val rows: Int,
    val failedBatches: Int = 0,
    /** True when a run was skipped because another refresh was already in flight. */
    val skipped: Boolean = false
)

/**
 * Daily aggregated news sentiment per asset, backed by EODHD's `/api/sentiments` feed and cached
 * in [NewsSentimentDaily].
 *
 * [refresh] fans out over every active asset on an EODHD-enabled market (`eodhd.markets`
 * allowlist), [EodhdNewsProperties.sentimentBatchSize] symbols per call. Assets are partitioned
 * before batching: never-seen assets ask for the full [EodhdNewsProperties.sentimentInitialDays]
 * window, assets with history ask from the earliest "latest stored day minus [OVERLAP_DAYS]" in
 * their batch, so a new asset never drags a whole batch of known assets back through their full
 * history. The overlap exists because EODHD keeps counting articles for a day after it first
 * reports it; a row is only rewritten when the count or score actually moved. One failed batch
 * is logged and skipped; the others still persist. Runs are single-flight — the nightly schedule
 * and the admin POST share one [ReentrantLock], and a second caller gets a `skipped` result.
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
    private val running = ReentrantLock()

    fun refresh(): SentimentRefreshResult {
        if (!running.tryLock()) {
            log.info("Sentiment refresh already running; skipping this call")
            return SentimentRefreshResult(assets = 0, calls = 0, rows = 0, skipped = true)
        }
        try {
            return doRefresh()
        } finally {
            running.unlock()
        }
    }

    private fun doRefresh(): SentimentRefreshResult {
        val assets =
            assetFinder
                .findActiveAssetsForPricing()
                .filter { eodhdConfig.supportsMarketCode(eodhdConfig.markets, it.marketCode) }
        val today = dateUtils.date
        val latest = repo.findLatestPriceDates(assets.map { it.id }).associate { it.assetId to it.priceDate }
        val (known, fresh) = assets.partition { latest.containsKey(it.id) }
        val tally = Tally()
        listOf(fresh, known).forEach { partition ->
            partition.chunked(newsProperties.sentimentBatchSize).forEach { batch ->
                val from = batch.minOf { fromDate(it.id, latest, today) }
                fetchBatch(batch, from, tally)
            }
        }
        val result = SentimentRefreshResult(assets.size, tally.calls, tally.rows, tally.failed)
        log.info("Sentiment refresh: {}", result)
        return result
    }

    private fun fetchBatch(
        batch: List<Asset>,
        from: LocalDate,
        tally: Tally
    ) {
        // Keyed upper-case (locale-fixed) to match EodhdSentimentParser's keys however EODHD echoes them.
        val bySymbol = batch.groupBy { eodhdConfig.getPriceCode(it).uppercase(Locale.ROOT) }
        val symbols = bySymbol.keys.joinToString(",")
        tally.calls++
        val parsed =
            runCatching {
                EodhdSentimentParser.parse(eodhdProxy.getSentiments(symbols, from.toString(), eodhdConfig.apiKey))
            }.getOrElse {
                tally.failed++
                log.warn("Sentiment batch failed for [{}] from {}: {}", symbols, from, it.message)
                return
            }
        tally.rows += upsert(bySymbol, parsed, from)
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
        latest: Map<String, LocalDate>,
        today: LocalDate
    ): LocalDate {
        val latestDate = latest[assetId]
        return if (latestDate == null) {
            today.minusDays(newsProperties.sentimentInitialDays)
        } else {
            latestDate.minusDays(OVERLAP_DAYS)
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
        val toSave = mutableListOf<NewsSentimentDaily>()
        // Symbols EODHD did not answer for never appear; ones we did not ask for are skipped but
        // logged, because a silent mismatch here would read as "no coverage" rather than a routing bug.
        val unmatched = parsed.keys - bySymbol.keys
        if (unmatched.isNotEmpty()) {
            log.warn("Sentiment batch returned symbols matching no requested asset: {}", unmatched)
        }
        parsed.forEach { (symbol, points) ->
            bySymbol[symbol].orEmpty().forEach { asset ->
                points.filter { it.date >= from }.forEach { point ->
                    val key = asset.id to point.date
                    val row = existing[key]
                    if (row == null) {
                        val created =
                            NewsSentimentDaily(asset.id, point.date, symbol, point.count, point.normalized, fetchedAt)
                        existing[key] = created
                        toSave += created
                    } else if (changed(row, point)) {
                        // Unchanged rows are left alone — fetchedAt included — so a quiet day costs no UPDATE.
                        row.symbol = symbol
                        row.articleCount = point.count
                        row.normalized = point.normalized
                        row.fetchedAt = fetchedAt
                        toSave += row
                    }
                }
            }
        }
        repo.saveAll(toSave)
        return toSave.size
    }

    private fun changed(
        row: NewsSentimentDaily,
        point: SentimentPoint
    ): Boolean = row.articleCount != point.count || row.normalized.compareTo(point.normalized) != 0

    /** Per-run counters shared across batches. */
    private class Tally {
        var calls = 0
        var rows = 0
        var failed = 0
    }

    companion object {
        private val log = LoggerFactory.getLogger(NewsSentimentService::class.java)

        /** Trailing days re-read on every run so late-arriving article counts overwrite the stored day. */
        const val OVERLAP_DAYS = 2L
    }
}