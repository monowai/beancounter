package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.ProviderArguments
import com.beancounter.marketdata.providers.ProviderArguments.Companion.getInstance
import com.beancounter.marketdata.providers.eodhd.model.EodhdBulkPrice
import com.beancounter.marketdata.providers.eodhd.model.EodhdPrice
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.time.LocalDate

/**
 * End-of-day EODHD prices: a request's batches, one bulk call per market where it can, and an
 * asset's history for backfill.
 */
@Component
class EodhdDailyPrices(
    private val eodhdConfig: EodhdConfig,
    private val eodhdProxy: EodhdProxy,
    private val eodhdAdapter: EodhdAdapter,
    private val dateUtils: DateUtils
) {
    private val log = LoggerFactory.getLogger(EodhdDailyPrices::class.java)

    fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> {
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

    fun backFill(
        asset: Asset,
        fromDate: LocalDate
    ): PriceResponse {
        val symbol = eodhdConfig.getPriceCode(asset)
        val dateTo = dateUtils.today()
        val rows = eodhdProxy.getHistory(symbol, fromDate.toString(), dateTo, eodhdConfig.apiKey)
        return PriceResponse(eodhdAdapter.toMarketData(asset, LocalDate.parse(dateTo), rows))
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
    ): Result<List<EodhdPrice>> =
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
        EodhdPrice(
            date = bulk.date,
            open = bulk.open,
            high = bulk.high,
            low = bulk.low,
            close = bulk.close,
            adjustedClose = bulk.adjustedClose,
            volume = bulk.volume
        )
}