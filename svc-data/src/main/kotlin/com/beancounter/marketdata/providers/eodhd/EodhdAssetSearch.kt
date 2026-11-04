package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.model.Market
import com.beancounter.marketdata.providers.AssetSearchProvider
import com.beancounter.marketdata.providers.eodhd.model.EodhdSearchResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * EODHD's asset search, mapping each hit's EODHD exchange back to the owning BC market.
 */
@Component
class EodhdAssetSearch(
    private val eodhdConfig: EodhdConfig,
    private val eodhdProxy: EodhdProxy
) : AssetSearchProvider {
    private val log = LoggerFactory.getLogger(EodhdAssetSearch::class.java)

    override fun getId(): String = EodhdPriceService.ID

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
        // Currency pairs are priced from the stored FX rates (FxPairPriceService), so EODHD's
        // FOREX rows are never offered as assets — one source per pair.
        if (row.exchange.equals(FOREX_EXCHANGE, ignoreCase = true)) return null
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
                0 -> null
                1 -> candidates.first()
                else -> resolveAmbiguousMarket(candidates, exchange, currency)
            }
        return resolved?.let { runCatching { eodhdConfig.marketService.canonical(it.code) }.getOrDefault(it) }
    }

    /**
     * Active markets are the real persistence targets; inactive aggregators (NASDAQ/NYSE/AMEX)
     * collapse to US in [resolveMarket].
     */
    private fun resolveAmbiguousMarket(
        candidates: List<Market>,
        exchange: String,
        currency: String?
    ): Market? {
        val preferred = candidates.filter { it.active }.ifEmpty { candidates }
        val normalized = normalizeCurrency(currency)
        return preferred.firstOrNull { it.currencyId.equals(normalized, ignoreCase = true) }
            ?: preferred.firstOrNull()
            ?: runCatching { eodhdConfig.marketService.getMarket(exchange) }.getOrNull()
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

    private companion object {
        const val FOREX_EXCHANGE = "FOREX"
        const val EODHD_ALIAS = "eodhd"
    }
}