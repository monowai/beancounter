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
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
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
        val rows =
            try {
                eodhdProxy.getPrice(symbol, priceDate, eodhdConfig.apiKey)
            } catch (e: HttpClientErrorException) {
                log.warn("EODHD {} for {} on {} — skipping", e.statusCode, symbol, priceDate)
                return emptyList()
            }
        return eodhdAdapter.toMarketData(asset, LocalDate.parse(priceDate), rows)
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
            } catch (e: HttpClientErrorException) {
                log.warn(
                    "EODHD bulk {} for batch {} ({} symbols) on {} — falling back to per-symbol",
                    e.statusCode,
                    batchId,
                    symbols.size,
                    priceDate
                )
                return symbols.flatMap { fetchSingle(providerArguments, it, priceDate) }
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
                .map { row ->
                    AssetSearchResult(
                        symbol = row.code,
                        name = row.name,
                        type = row.type ?: "Equity",
                        region = row.exchange,
                        currency = row.currency,
                        market = row.exchange
                    )
                }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("EODHD search for '{}' failed: {}", keyword, e.message)
            emptyList()
        }
    }

    companion object {
        const val ID = "EODHD"
    }
}