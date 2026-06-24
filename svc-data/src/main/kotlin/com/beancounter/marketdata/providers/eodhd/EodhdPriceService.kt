package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import com.beancounter.marketdata.providers.ProviderArguments
import com.beancounter.marketdata.providers.ProviderArguments.Companion.getInstance
import com.beancounter.marketdata.providers.eodhd.model.EodhdBulkPrice
import com.beancounter.marketdata.providers.eodhd.model.EodhdSearchResult
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import java.time.LocalDate

/**
 * Facade for the EODHD market data provider — see https://eodhd.com.
 *
 * EODHD is opt-in: it ships disabled by default (empty `markets` allowlist, `key=demo`) so registered
 * AlphaVantage / MarketStack / Morningstar routing is unchanged until an operator explicitly enables
 * markets via `BEANCOUNTER_MARKET_PROVIDERS_EODHD_MARKETS`.
 */
@Service
class EodhdPriceService(
    private val eodhdConfig: EodhdConfig,
    private val eodhdProxy: EodhdProxy,
    private val eodhdAdapter: EodhdAdapter,
    private val dateUtils: DateUtils
) : MarketDataPriceProvider {
    private val log = LoggerFactory.getLogger(EodhdPriceService::class.java)

    @PostConstruct
    fun logStatus() {
        log.info(
            "BEANCOUNTER_MARKET_PROVIDERS_EODHD_KEY: {}",
            if (eodhdConfig.apiKey.equals("demo", ignoreCase = true)) {
                "demo"
            } else {
                "** Redacted **"
            }
        )
    }

    override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> {
        val providerArguments = getInstance(priceRequest, eodhdConfig, dateUtils)
        val results = mutableListOf<MarketData>()
        for (batchId in providerArguments.batch.keys) {
            val symbols = providerArguments.getAssets(batchId)
            if (symbols.isEmpty()) continue
            val priceDate = providerArguments.getBatchConfigs(batchId)?.date ?: dateUtils.today()
            results.addAll(
                if (symbols.size > 1) {
                    fetchBulk(providerArguments, batchId, symbols, priceDate)
                } else {
                    fetchSingle(providerArguments, symbols.first(), priceDate)
                }
            )
        }
        return results
    }

    /**
     * Single-symbol fan-out path — kept for batches of size 1 (e.g. an ad-hoc valuation
     * for one asset). 4xx is swallowed per [getMarketData]'s batch-isolation guarantee.
     */
    private fun fetchSingle(
        providerArguments: ProviderArguments,
        symbol: String,
        priceDate: String
    ): List<MarketData> {
        val asset = providerArguments.getAsset(symbol)
        // A 4xx for one symbol must not abort the whole batch — bc-position
        // schedules valuations across every portfolio, so one bad ticker would
        // otherwise halt every portfolio sequenced after it.
        return fetchSymbolRows(symbol, priceDate).fold(
            onSuccess = { rows -> eodhdAdapter.toMarketData(asset, LocalDate.parse(priceDate), rows) },
            onFailure = { e ->
                log.warn("EODHD error for {} on {} — skipping: {}", symbol, priceDate, e.message)
                emptyList()
            }
        )
    }

    /**
     * Fetch one symbol's rows, capturing a [RestClientException] as a failed [Result] rather
     * than logging here — callers decide whether to log per-symbol (true single fetch) or as
     * one batch summary (bulk fallback). Broad RestClientException, not just 4xx: also covers
     * body-extraction failures when EODHD returns a non-array error payload, and transient
     * I/O (e.g. a DNS/connection blip during the refresh burst).
     */
    private fun fetchSymbolRows(
        symbol: String,
        priceDate: String
    ): Result<List<com.beancounter.marketdata.providers.eodhd.model.EodhdPrice>> =
        try {
            Result.success(eodhdProxy.getPrice(symbol, priceDate, eodhdConfig.apiKey))
        } catch (e: RestClientException) {
            Result.failure(e)
        }

    /**
     * Per-symbol fallback for a failed bulk call. Collapses what used to be one WARN per
     * failed symbol into a single batch summary — a transient I/O blip during the refresh
     * burst previously emitted ~50 identical warnings (one per US symbol).
     */
    private fun fetchPerSymbol(
        providerArguments: ProviderArguments,
        symbols: List<String>,
        priceDate: String
    ): List<MarketData> {
        val parsedDate = LocalDate.parse(priceDate)
        val results = mutableListOf<MarketData>()
        val failures = mutableListOf<String>()
        var lastError: String? = null
        for (symbol in symbols) {
            val asset = providerArguments.getAsset(symbol)
            fetchSymbolRows(symbol, priceDate).fold(
                onSuccess = { rows -> results.addAll(eodhdAdapter.toMarketData(asset, parsedDate, rows)) },
                onFailure = { e ->
                    failures.add(symbol)
                    lastError = e.message
                }
            )
        }
        if (failures.isNotEmpty()) {
            log.warn(
                "EODHD per-symbol fallback on {}: {}/{} symbols failed ({}): {}",
                priceDate,
                failures.size,
                symbols.size,
                lastError,
                failures.joinToString(",")
            )
        }
        return results
    }

    /**
     * Multi-symbol bulk path — collapses N per-symbol HTTP round-trips into one
     * `/api/eod-bulk-last-day/{exchange}` call. All assets in a batch share a single
     * market (enforced upstream by `ProviderArguments.split`), so the exchange code is
     * unambiguous. A symbol that's missing from the bulk response gets a zero-close row
     * via [EodhdAdapter.toMarketData] for parity with the per-symbol path.
     */
    private fun fetchBulk(
        providerArguments: ProviderArguments,
        batchId: Int,
        symbols: List<String>,
        priceDate: String
    ): List<MarketData> {
        val assetsByDpKey = symbols.associateWith { providerArguments.getAsset(it) }
        val market = assetsByDpKey.values.first().market
        val exchange = eodhdConfig.getExchange(market)
        val csv = symbols.joinToString(",")

        val bulkRows: List<EodhdBulkPrice> =
            try {
                eodhdProxy.getBulkPrices(exchange, csv, priceDate, eodhdConfig.apiKey)
            } catch (e: RestClientException) {
                // Broad RestClientException, not just HttpClientErrorException: a non-array
                // EODHD error body throws "Error while extracting response for type
                // [EodhdBulkPrice[]]" (a body-extraction failure, not a 4xx). Falling back
                // to per-symbol keeps the good symbols instead of 500ing the whole
                // valuation and zeroing every portfolio in the cycle (POSITION-2F).
                log.warn(
                    "EODHD bulk error for batch {} ({} symbols) on {} — falling back to per-symbol: {}",
                    batchId,
                    symbols.size,
                    priceDate,
                    e.message
                )
                return fetchPerSymbol(providerArguments, symbols, priceDate)
            }

        val rowsByCode = bulkRows.groupBy { it.code.uppercase() }
        val parsedDate = LocalDate.parse(priceDate)
        return assetsByDpKey.flatMap { (_, asset) ->
            val matched = rowsByCode[asset.code.uppercase()].orEmpty().map(::toEodhdPrice)
            eodhdAdapter.toMarketData(asset, parsedDate, matched)
        }
    }

    private fun toEodhdPrice(bulk: EodhdBulkPrice) =
        com.beancounter.marketdata.providers.eodhd.model.EodhdPrice(
            date = bulk.date,
            open = bulk.open,
            high = bulk.high,
            low = bulk.low,
            close = bulk.close,
            adjustedClose = bulk.adjustedClose,
            volume = bulk.volume
        )

    override fun getId(): String = ID

    override fun isMarketSupported(market: Market): Boolean =
        if (eodhdConfig.markets.isNullOrBlank()) {
            false
        } else {
            eodhdConfig.markets!!.contains(market.code)
        }

    override fun getDate(
        market: Market,
        priceRequest: PriceRequest
    ): LocalDate =
        eodhdConfig.getMarketDate(
            market,
            priceRequest.date
        )

    override fun backFill(
        asset: Asset,
        fromDate: LocalDate
    ): PriceResponse {
        val symbol = eodhdConfig.getPriceCode(asset)
        val dateTo = dateUtils.today()
        val rows = eodhdProxy.getHistory(symbol, fromDate.toString(), dateTo, eodhdConfig.apiKey)
        return PriceResponse(eodhdAdapter.toMarketData(asset, LocalDate.parse(dateTo), rows))
    }

    override fun isApiSupported(): Boolean = true

    // EODHD `/api/eod` ships `adjusted_close` per row; `EodhdAdapter` persists
    // that value as `MarketData.close` (see PR #875). SplitAdjuster must skip
    // dividing EODHD rows so corporate_event splits don't double-adjust them.
    override fun shipsAdjustedClose(): Boolean = true

    /**
     * Search EODHD `/api/search/{query}` for matches. Gated on the EODHD markets allowlist —
     * an unconfigured deployment returns empty so [AssetSearchService] falls back to the legacy
     * FIGI / AlphaVantage chain. `market` is informational only at this layer (EODHD searches
     * globally and returns the exchange per result); the caller decides whether to filter.
     */
    override fun searchAssets(
        keyword: String,
        market: String?
    ): List<AssetSearchResult> {
        if (eodhdConfig.markets.isNullOrBlank()) return emptyList()
        return try {
            eodhdProxy
                .searchAssets(keyword, eodhdConfig.apiKey)
                .mapNotNull { row -> toSearchResult(row) }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("EODHD search for '{}' failed: {}", keyword, e.message)
            emptyList()
        }
    }

    /**
     * Project an EODHD search row onto an [AssetSearchResult], mapping EODHD's exchange code back to
     * the owning BC market code.
     *
     * EODHD reports its own exchange (e.g. `LSE`), but the UI posts the result's `market` to
     * `/api/assets`, which only accepts BC market codes — posting the raw exchange trips "Unable to
     * resolve market code" → 404 (asset creation disables alias resolution). [resolveMarket]
     * resolves the exchange via the alias map (`LSE` → `LON`/`LSE`); rows whose exchange maps to no
     * BC market are dropped rather than surfaced with an un-postable code — mirroring the FIGI path.
     */
    private fun toSearchResult(row: EodhdSearchResult): AssetSearchResult? {
        val market = resolveMarket(row.exchange, row.currency)
        if (market == null) {
            log.debug("Dropping EODHD result {} — exchange {} maps to no BC market", row.code, row.exchange)
            return null
        }
        return AssetSearchResult(
            symbol = row.code,
            name = row.name,
            type = row.type ?: "Equity",
            region = market.code,
            currency = row.currency,
            market = market.code
        )
    }

    /**
     * Resolve an EODHD [exchange] to the owning BC [Market], disambiguating on the instrument
     * [currency] when several BC markets share one EODHD exchange.
     *
     * EODHD reports a single exchange code (`LSE`) for all of London, but BC splits London into
     * `LON` (currencyId GBP, pence-quoted UK equities) and `LSE` (currencyId USD, USD-denominated
     * London ETFs) — both carrying `aliases.eodhd = "LSE"`. Picking the first/direct-code match
     * mis-keys GBP equities to LSE/USD, breaking price caching (currency mismatch → re-fetch every
     * call). So for an ambiguous exchange we route on the normalized EODHD currency.
     *
     * The US `eodhd` alias is shared by the inactive aggregator markets (NASDAQ/NYSE/AMEX) and the
     * active `US` market — all USD, so currency can't disambiguate them. We prefer active candidates
     * and, when the resolved code is an aggregator, collapse to canonical `US` via [MarketService].
     *
     * @return the resolved market, or null when no BC market addresses the exchange.
     */
    private fun resolveMarket(
        exchange: String,
        currency: String?
    ): Market? {
        val candidates =
            eodhdConfig.marketService
                .getMarketMap()
                .values
                .filter { market ->
                    market.code.equals(exchange, ignoreCase = true) ||
                        market.getAlias(EODHD_ALIAS)?.equals(exchange, ignoreCase = true) == true
                }
        val resolved =
            when (candidates.size) {
                0 -> {
                    return null
                }
                1 -> {
                    candidates.first()
                }
                else -> {
                    // Active markets are the real persistence targets; inactive aggregators
                    // (NASDAQ/NYSE/AMEX) collapse to US below.
                    val preferred = candidates.filter { it.active }.ifEmpty { candidates }
                    val normalized = normalizeCurrency(currency)
                    preferred.firstOrNull { it.currencyId.equals(normalized, ignoreCase = true) }
                        ?: preferred.firstOrNull()
                        ?: runCatching { eodhdConfig.marketService.getMarket(exchange) }.getOrNull()
                        ?: return null
                }
            }
        return runCatching { eodhdConfig.marketService.canonical(resolved.code) }.getOrDefault(resolved)
    }

    /**
     * Map an EODHD currency to the BC market's `currencyId`. EODHD quotes London pence as `GBX`
     * (BC's LON market is currencyId GBP with the 0.01 multiplier), so GBX/GBp/GBX. all collapse to
     * GBP; every other currency is uppercased as-is. A null currency yields an empty string so the
     * ambiguous-exchange resolver falls through to its safe default.
     */
    private fun normalizeCurrency(currency: String?): String {
        val raw = currency?.trim()?.uppercase()?.removeSuffix(".") ?: return ""
        return if (raw == "GBX" || raw == "GBP") "GBP" else raw
    }

    companion object {
        const val ID = "EODHD"
        private const val EODHD_ALIAS = "eodhd"
    }
}