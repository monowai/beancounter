package com.beancounter.marketdata.providers.fx

import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.providers.AssetSearchProvider
import com.beancounter.marketdata.providers.fx.FxPairPriceService.Companion.CURRENCY_LENGTH
import com.beancounter.marketdata.providers.fx.FxPairPriceService.Companion.ID
import com.beancounter.marketdata.providers.fx.FxPairPriceService.Companion.PAIR_LENGTH
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * Finds currency pairs of the configured currencies, the assets [FxPairPriceService] prices.
 */
@Component
class FxPairSearch(
    private val currencyService: CurrencyService
) : AssetSearchProvider {
    override fun getId(): String = ID

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

    private companion object {
        const val CATEGORY = "Currency"
        const val FX_PREFIX = "FX:"
        const val YAHOO_SUFFIX = "=X"
        val WHITESPACE = Regex("\\s+")
        val ISO_CODES: Set<String> =
            java.util.Currency
                .getAvailableCurrencies()
                .map { it.currencyCode }
                .toSet()
    }
}