package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import com.beancounter.marketdata.providers.ProviderArguments.Companion.getInstance
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
            val symbol = providerArguments.batch[batchId] ?: continue
            val asset = providerArguments.getAsset(symbol)
            val priceDate = providerArguments.getBatchConfigs(batchId)?.date ?: dateUtils.today()
            // A 4xx for one symbol must not abort the whole batch — bc-position
            // schedules valuations across every portfolio, so one bad ticker would
            // otherwise halt every portfolio sequenced after it.
            val rows =
                try {
                    eodhdProxy.getPrice(symbol, priceDate, eodhdConfig.apiKey)
                } catch (e: HttpClientErrorException) {
                    log.warn("EODHD {} for {} on {} — skipping", e.statusCode, symbol, priceDate)
                    continue
                }
            results.addAll(
                eodhdAdapter.toMarketData(asset, LocalDate.parse(priceDate), rows)
            )
        }
        return results
    }

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