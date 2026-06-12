package com.beancounter.marketdata.assets

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Market
import com.beancounter.marketdata.markets.MarketService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Guards against silently creating a phantom asset on the wrong exchange.
 *
 * The bc-view trade form defaults an asset's market from the portfolio currency
 * (USD → US), so a USD-denominated foreign listing — e.g. Vanguard VUAA on the
 * LSE — gets created as `VUAA` on `US`. EODHD then can't price `VUAA.US`, which
 * cascaded into a broken valuation and an empty retirement projection.
 *
 * When the enricher chain can't resolve a ticker on the supplied market (blank
 * name), the search providers are asked where the ticker actually trades. If it
 * resolves on other exchanges but NOT one equivalent to the supplied market, the
 * create is rejected with the valid market(s).
 *
 * Equivalence is by **provider exchange**, not raw BC market code: US / NASDAQ /
 * NYSE / AMEX all share the EODHD exchange `US`, so a ticker the search reports on
 * `NASDAQ` must not be rejected for a `US` create. The eodhd alias (falling back to
 * the market code) is the canonical exchange key.
 *
 * Fail-open by design: no search hits (a genuinely new or private ticker) or a
 * search error never blocks creation — only a positive "listed elsewhere, not
 * here" rejects.
 */
@Service
class AssetMarketVerifier(
    private val assetSearchService: AssetSearchService,
    private val marketService: MarketService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * @throws BusinessException when [code] resolves on other exchanges but not one
     *   equivalent to [market].
     */
    fun verify(
        code: String,
        market: Market,
        enrichedName: String?
    ) {
        // An enricher resolved the ticker on this market — trust it, no lookup.
        if (!enrichedName.isNullOrBlank()) return
        // Internal / synthetic markets carry no external exchange listing to check.
        if (market.code.uppercase() in EXEMPT_MARKETS) return

        val ticker = code.uppercase()
        val listedExchanges =
            runCatching { assetSearchService.search(ticker, null).data }
                .onFailure { log.debug("Market verify search failed for {}: {}", ticker, it.message) }
                .getOrDefault(emptyList())
                .filter { it.symbol.equals(ticker, ignoreCase = true) && !it.market.isNullOrBlank() }
                .mapNotNull { runCatching { marketService.getMarket(it.market!!) }.getOrNull() }
                .map { exchangeKey(it) }
                .toSet()

        if (listedExchanges.isNotEmpty() && exchangeKey(market) !in listedExchanges) {
            throw BusinessException(
                "$code was not found on market ${market.code}. " +
                    "It is listed on: ${listedExchanges.sorted().joinToString(", ")}. " +
                    "Re-enter the trade selecting the correct market."
            )
        }
    }

    // Canonical exchange identity: the EODHD exchange the market routes to (US, LSE,
    // AS, …), so equivalent BC markets (US/NASDAQ/NYSE/AMEX → US) compare equal.
    // Falls back to the market code for markets without an eodhd alias (NZX, SGX).
    private fun exchangeKey(market: Market): String = (market.getAlias("eodhd") ?: market.code).uppercase()

    private companion object {
        // Internal/synthetic markets with no external exchange listing to verify.
        val EXEMPT_MARKETS = setOf("CASH", "PRIVATE", "RE", "INDEX", "MUTF")
    }
}