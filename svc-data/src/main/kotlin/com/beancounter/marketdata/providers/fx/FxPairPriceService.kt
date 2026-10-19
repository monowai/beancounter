package com.beancounter.marketdata.providers.fx

import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.currency.CurrencyService
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
    private val currencyService: CurrencyService,
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
                fxRateService.getHistoricalRates(base, quote, fromDate, dateUtils.date)
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

    /**
     * A pair typed as six letters (NZDSGD, NZD/SGD, NZDSGD=X, FX:NZDSGD) whose halves are two
     * different configured currencies. On an FX-scoped search one currency (NZD) also lists its
     * pairs with every other configured currency; unscoped searches skip that to stay quiet.
     */
    override fun searchAssets(
        keyword: String,
        market: String?
    ): List<AssetSearchResult> {
        if (market != null && !ID.equals(market.trim(), ignoreCase = true)) return emptyList()
        val term = normalise(keyword)
        val currencies = configuredCurrencies()
        return when {
            term.length == PAIR_LENGTH -> {
                val base = term.take(CURRENCY_LENGTH)
                val quote = term.takeLast(CURRENCY_LENGTH)
                if (base != quote && base in currencies && quote in currencies) {
                    listOf(searchResult(base, quote))
                } else {
                    emptyList()
                }
            }
            term.length == CURRENCY_LENGTH && market != null && term in currencies -> {
                currencies.filter { it != term }.map { searchResult(term, it) }
            }
            else -> {
                emptyList()
            }
        }
    }

    private fun searchResult(
        base: String,
        quote: String
    ) = AssetSearchResult(
        symbol = base + quote,
        name = "$base/$quote",
        type = CATEGORY,
        region = ID,
        currency = quote,
        market = ID
    )

    /** Configured codes that are ISO currencies; the table can also hold index codes (GSPC). */
    private fun configuredCurrencies(): List<String> =
        currencyService
            .currencies()
            .map { it.code.uppercase(Locale.ROOT) }
            .filter { it in ISO_CODES }
            .distinct()

    private fun normalise(keyword: String): String =
        keyword
            .trim()
            .uppercase(Locale.ROOT)
            .removePrefix(FX_PREFIX)
            .removeSuffix(YAHOO_SUFFIX)
            .replace("/", "")
            .replace(WHITESPACE, "")

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
        private const val CATEGORY = "Currency"
        private const val PAIR_LENGTH = 6
        private const val CURRENCY_LENGTH = 3
        private const val CLOSE_SCALE = 6
        private const val FX_PREFIX = "FX:"
        private const val YAHOO_SUFFIX = "=X"
        private val WHITESPACE = Regex("\\s+")
        private val ISO_CODES: Set<String> =
            java.util.Currency
                .getAvailableCurrencies()
                .map { it.currencyCode }
                .toSet()
    }
}