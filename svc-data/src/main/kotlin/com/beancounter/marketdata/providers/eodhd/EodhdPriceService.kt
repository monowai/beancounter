package com.beancounter.marketdata.providers.eodhd

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

    /**
         * Determine the effective date for the given market based on provider configuration and the requested date.
         *
         * @param market The market for which to compute the effective date.
         * @param priceRequest The incoming price request whose `date` may be used or adjusted by configuration.
         * @return The LocalDate that should be used when fetching prices for the specified market.
         */
        override fun getDate(
        market: Market,
        priceRequest: PriceRequest
    ): LocalDate =
        eodhdConfig.getMarketDate(
            market,
            priceRequest.date
        )

    /**
     * Fetches historical price data for an asset starting from a specified date and returns it as a PriceResponse.
     *
     * @param asset The asset to backfill prices for.
     * @param fromDate The earliest date (inclusive) from which to retrieve historical prices.
     * @return A PriceResponse containing the asset's market data from `fromDate` through today's date.
     */
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

    companion object {
        const val ID = "EODHD"
    }
}