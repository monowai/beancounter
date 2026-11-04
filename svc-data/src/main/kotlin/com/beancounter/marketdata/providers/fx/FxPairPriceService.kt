package com.beancounter.marketdata.providers.fx

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.fx.FxHistoryService
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

/**
 * Prices currency pairs on the FX market (NZDSGD: SGD per NZD) from the stored USD->X rates that
 * [FxRateService] keeps for every configured currency, triangulating the cross. A watched pair
 * therefore reads the same rate a portfolio valuation converts with, and needs no market-data
 * vendor of its own.
 */
@Service
class FxPairPriceService(
    private val fxRateService: FxRateService,
    private val fxHistoryService: FxHistoryService,
    private val dateUtils: DateUtils = DateUtils()
) : MarketDataPriceProvider {
    private val log = LoggerFactory.getLogger(FxPairPriceService::class.java)

    override fun getId(): String = ID

    override fun isMarketSupported(market: Market): Boolean = ID.equals(market.code, ignoreCase = true)

    // Rates are fetched and stored by FxRateService; true lets the pair's close persist as
    // price history and fall back to the last stored close when no rate is available.
    override fun isApiSupported(): Boolean = true

    override fun getDate(
        market: Market,
        priceRequest: PriceRequest
    ): LocalDate = fxRateService.getDate(priceRequest.date)

    override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> =
        priceRequest.assets
            .mapNotNull { it.resolvedAsset }
            .mapNotNull { asset -> price(asset, priceRequest.date) }

    private fun price(
        asset: Asset,
        rateDate: String
    ): MarketData? {
        val (base, quote) = legs(asset.code) ?: return null
        return try {
            val rate = fxRateService.getPairRate(base, quote, rateDate)
            MarketData(asset = asset, priceDate = rate.date, close = scaled(rate.rate), source = ID)
        } catch (e: RuntimeException) {
            log.warn("No FX rate for pair {} on {}: {}", asset.code, rateDate, noRate(e).message)
            null
        }
    }

    // Kotlin has no multi-catch; the "no rate" failures (unknown currency, no stored or fetchable
    // rates, a leg missing from the table) share only RuntimeException. Anything else is rethrown.
    private fun noRate(e: RuntimeException): RuntimeException {
        if (e !is BusinessException && e !is SystemException && e !is IllegalArgumentException) throw e
        return e
    }

    override fun backFill(
        asset: Asset,
        fromDate: LocalDate
    ): PriceResponse {
        val (base, quote) = legs(asset.code) ?: return PriceResponse()
        val history =
            try {
                fxHistoryService.getHistoricalRates(base, quote, fromDate, dateUtils.date)
            } catch (e: RuntimeException) {
                log.warn("No FX rate history for pair {} from {}: {}", asset.code, fromDate, noRate(e).message)
                return PriceResponse()
            }
        return PriceResponse(
            history.data.map { point ->
                MarketData(asset = asset, priceDate = point.date, close = scaled(point.rate), source = ID)
            }
        )
    }

    private fun legs(code: String): Pair<String, String>? {
        if (code.length != PAIR_LENGTH) {
            log.warn("FX asset {} is not a six-letter currency pair", code)
            return null
        }
        val upper = code.uppercase(Locale.ROOT)
        return upper.take(CURRENCY_LENGTH) to upper.takeLast(CURRENCY_LENGTH)
    }

    // MarketData stores close at scale 6; round here so the returned price matches the stored one.
    private fun scaled(rate: BigDecimal): BigDecimal = rate.setScale(CLOSE_SCALE, RoundingMode.HALF_UP)

    companion object {
        const val ID = "FX"
        internal const val PAIR_LENGTH = 6
        internal const val CURRENCY_LENGTH = 3
        private const val CLOSE_SCALE = 6
    }
}