package com.beancounter.marketdata.price

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.TestEnvironmentUtils
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.event.EventProducer
import com.beancounter.marketdata.persistence.ConflictTolerantWriter
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.TreeMap

/**
 * Persist prices obtained from providers and detect if Corporate Events need to be dispatched.
 */
@Service
class PriceService(
    private val marketDataRepo: MarketDataRepo,
    private val cashUtils: CashUtils,
    private val conflictTolerantWriter: ConflictTolerantWriter,
    private val dayChange: DayChange,
    private val eventProducer: EventProducer? = null,
    private val cacheInvalidationProducer: CacheInvalidationProducer? = null
) {
    private val log = LoggerFactory.getLogger(PriceService::class.java)

    /**
     * Persistence and distribution of MarketData objects.
     * If previousClose is not provided by the API, it is calculated from the previous day's close.
     *
     * IMPORTANT: Prices with close <= 0 are rejected as invalid data from the provider.
     * A zero or negative price indicates a provider issue and should never be stored.
     *
     * No longer @Transactional: [buildCreateSet]'s dedup read and [persistInChunks]'s
     * write can never be atomic against a concurrent writer doing the same thing for
     * the same asset — the natural key is (source, asset_id, priceDate), not the
     * generated `id`, so a second caller's INSERT for a row this call already wrote
     * doesn't collide until flush. Wrapping the whole method in one transaction only
     * widened that window and held an extra connection for its length. Instead each
     * chunk is written through [ConflictTolerantWriter], which persists in its own
     * REQUIRES_NEW transaction and, on a unique-constraint conflict, retries row by
     * row and skips only the rows a concurrent writer already committed. The
     * returned rows are always the application-constructed inputs (plain in-memory
     * references), never Hibernate-managed instances — safe to use after this
     * method returns regardless of what the writer actually persisted.
     */
    fun handle(priceResponse: PriceResponse): Iterable<MarketData> {
        val eligible = priceResponse.data.filter { !cashUtils.isCash(it.asset) && isValidPrice(it) }

        // Batched per asset (#1096): DB state doesn't change mid-call — saveAll
        // only happens after every group is built — so loading each asset's
        // pre-existing rows once up front yields identical per-row dedup /
        // previousClose answers to the old per-row query approach, at two
        // queries per asset instead of two queries per row.
        val batches =
            eligible
                .groupBy { it.asset.id }
                .values
                .map { rows -> buildCreateSet(rows) }
        val createSet = batches.flatMap { it.created }
        val relinked = batches.flatMap { it.relinked }

        priceResponse.data
            .filter { !cashUtils.isCash(it.asset) && isCorporateEvent(it) }
            .forEach { marketData ->
                eventProducer?.write(marketData)
            }

        return if (createSet.isEmpty()) {
            createSet
        } else {
            persistInChunks(createSet)
            persistInChunks(relinked)
            val dates = createSet.map { it.priceDate }.distinct()
            dates.forEach { cacheInvalidationProducer?.sendPriceEvent(it) }
            // Return the application-constructed rows. Each chunk was written in
            // ConflictTolerantWriter's own REQUIRES_NEW transaction, which has
            // already ended, so any managed instance from it would be detached
            // with associations that can't be trusted. createSet holds the same
            // data with plain in-memory references throughout.
            createSet
        }
    }

    /**
     * One asset's batch: the rows to insert, plus the stored rows whose
     * `previousClose` must be relinked because a new row now sits between them
     * and their former predecessor.
     */
    private data class AssetBatch(
        val created: List<MarketData>,
        val relinked: List<MarketData>
    )

    /**
     * Dedup + enrich one asset's rows against its pre-existing stored state,
     * loaded once for the whole group instead of per row.
     *
     * New rows are enriched in date order and each one joins the lookup as it is
     * enriched, so a multi-row batch from a provider that ships no previousClose
     * (EOD history) chains through itself rather than every row pointing at the
     * last row stored before the batch. A stored row whose predecessor is now a
     * new row — the row after a filled hole, inside or just past the batch window
     * — is relinked so its change no longer spans the hole.
     */
    private fun buildCreateSet(rows: List<MarketData>): AssetBatch {
        val asset = rows.first().asset
        val minDate = rows.minOf { it.priceDate }
        val maxDate = rows.maxOf { it.priceDate }

        val storedInRange = marketDataRepo.findByAssetIdAndPriceDateBetween(asset.id, minDate, maxDate)
        val existingDates = storedInRange.map { it.priceDate }.toSet()
        val priorRow =
            marketDataRepo
                .findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(asset, minDate)
                .orElse(null)
        val successorRow =
            marketDataRepo
                .findTop1ByAssetAndPriceDateGreaterThanOrderByPriceDateAsc(asset, maxDate)
                .orElse(null)

        // Keyed on priceDate alone while the table's uniqueness is
        // (source, asset_id, priceDate): when two providers hold rows for the
        // same date they are intentionally coalesced to one arbitrary source —
        // any provider's close is acceptable for previous-close resolution
        // (established price-source policy), and the per-row
        // findTop1ByAssetAndPriceDateLessThan query this replaced picked just
        // as arbitrarily on such ties.
        val byDate = TreeMap<LocalDate, MarketData>()
        storedInRange.forEach { byDate[it.priceDate] = it }
        priorRow?.let { byDate[it.priceDate] = it }
        val storedDates = byDate.keys.toSet()

        val created =
            rows
                .filter { it.priceDate !in existingDates }
                .sortedBy { it.priceDate }
                .map { marketData ->
                    val enriched =
                        dayChange.enrichWithPreviousClose(
                            marketData,
                            byDate.lowerEntry(marketData.priceDate)?.value
                        )
                    byDate[enriched.priceDate] = enriched
                    enriched
                }
        if (created.isEmpty()) {
            return AssetBatch(created, emptyList())
        }

        val relinked =
            (storedInRange + listOfNotNull(successorRow))
                .filter { stored ->
                    val predecessor = byDate.lowerEntry(stored.priceDate)?.value
                    predecessor != null && predecessor.priceDate !in storedDates
                }.map { stored ->
                    stored.previousClose = BigDecimal.ZERO
                    dayChange.enrichWithPreviousClose(stored, byDate.lowerEntry(stored.priceDate)?.value)
                }
        return AssetBatch(created, relinked)
    }

    /**
     * Persists in bounded-size chunks via [ConflictTolerantWriter]. Each chunk is
     * saved and flushed in its own short-lived REQUIRES_NEW transaction, so — as
     * before #1096 — no managed `MarketData` entity or Hibernate dirty-check
     * snapshot from one chunk stays alive for the rest of a large backfill (a year
     * ≈ 260 rows, a storm of concurrent backfills far more). The writer additionally
     * retries a chunk row by row and skips only the rows that collide with a
     * concurrent writer's already-committed insert on the (source, asset_id,
     * priceDate) unique constraint, instead of failing the whole call.
     */
    private fun persistInChunks(createSet: List<MarketData>) {
        createSet.chunked(SAVE_CHUNK_SIZE).forEach { chunk ->
            conflictTolerantWriter.saveAll(marketDataRepo, chunk)
        }
    }

    private fun isCorporateEvent(marketData: MarketData): Boolean =
        marketData.asset.isKnown && (isDividend(marketData) || isSplit(marketData))

    /**
     * Validates that a price is valid for storage.
     * A valid price must have a close price > 0.
     * Zero or negative prices indicate provider issues and are rejected.
     */
    private fun isValidPrice(marketData: MarketData): Boolean {
        val isValid = marketData.close > BigDecimal.ZERO
        if (!isValid) {
            log.warn(
                "Rejecting invalid price for {} on {}: close={} (must be > 0)",
                marketData.asset.code,
                marketData.priceDate,
                marketData.close
            )
        }
        return isValid
    }

    /**
     * Delete all prices.  Supports testing ONLY!
     *
     * CRITICAL: This method should NEVER be called in production!
     * It will permanently delete ALL market data history.
     */
    @Transactional
    fun purge() {
        // SAFEGUARD: Only allow purge in test environments
        if (!TestEnvironmentUtils.isTestEnvironment()) {
            throw IllegalStateException(
                "CRITICAL ERROR: purge() method called in non-test environment! " +
                    "This would delete ALL market data history. " +
                    "Current profile: ${System.getProperty("spring.profiles.active")} " +
                    "Classpath: ${System.getProperty("java.class.path")}"
            )
        }

        log.warn("PURGE OPERATION: Deleting ALL market data - this should only happen in tests!")
        marketDataRepo.deleteAll()
    }

    /**
     * Get market data for assets on a specific date (exact match only).
     */
    @Transactional
    fun getMarketData(
        assets: Collection<Asset>,
        date: LocalDate
    ): List<MarketData> =
        marketDataRepo.findByAssetInAndPriceDate(
            assets,
            date
        )

    /**
     * Get the most recent market data for each of [assets] on or before [date], keyed by
     * asset id. Used as a fallback when the market was closed on the requested date.
     *
     * Single batched query (DATA-6G) via
     * [MarketDataRepo.findLatestByAssetInAndPriceDateLessThanEqual] instead of one
     * findTop1By...LessThanEqual query per asset — a POST /api/prices request for N assets
     * on a market-closed day used to issue N such queries, flagged by Sentry as an N+1
     * (issue DATA-6G, 18 repetitions for an 18-asset request).
     */
    @Transactional
    fun getLatestMarketData(
        assets: Collection<Asset>,
        date: LocalDate
    ): Map<String, MarketData> {
        if (assets.isEmpty()) return emptyMap()
        return marketDataRepo
            .findLatestByAssetInAndPriceDateLessThanEqual(assets, date)
            .associateBy { it.asset.id }
    }

    /**
     * SAFEGUARD: Count market data records for an asset on a specific date
     */
    @Transactional
    fun getMarketDataCount(
        assetId: String,
        date: LocalDate
    ): Long = marketDataRepo.countByAssetIdAndPriceDate(assetId, date)

    companion object {
        // Batch size for the chunked saveAll() in persistInChunks. Flushing and
        // clearing the persistence context between chunks keeps the largest
        // backfills (multi-year, hundreds of rows) from holding every managed
        // entity in memory for the whole handle() call — see #1096.
        private const val SAVE_CHUNK_SIZE = 500
    }
}