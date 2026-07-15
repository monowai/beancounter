package com.beancounter.marketdata.providers.eodhd

import com.beancounter.marketdata.providers.eodhd.model.EodhdBulkPrice
import com.beancounter.marketdata.providers.eodhd.model.EodhdDividend
import com.beancounter.marketdata.providers.eodhd.model.EodhdFundamentals
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import com.beancounter.marketdata.providers.eodhd.model.EodhdPrice
import com.beancounter.marketdata.providers.eodhd.model.EodhdSearchResult
import com.beancounter.marketdata.providers.eodhd.model.EodhdSplit
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Thin RestClient wrapper for the EODHD HTTP API.
 *
 * Auth: `api_token` query parameter.
 * Response format: JSON arrays (forced via `fmt=json`).
 */
@Component
class EodhdGateway(
    @Qualifier("eodhdRestClient")
    private val restClient: RestClient,
    // Search calls fire from the header bar on every keystroke. Use the dedicated
    // short-timeout client (2s connect / 3s read) so a stalled EODHD edge can't drag
    // the user response out for the 5s/30s price-tier defaults — the coroutine
    // withTimeoutOrNull wrapper is cosmetic without a real HTTP-layer cap because
    // blocking RestClient calls aren't cooperatively cancellable.
    @Qualifier("eodhdSearchRestClient")
    private val searchRestClient: RestClient
) {
    /**
     * Single-day EOD price for one symbol.
     *
     * GET /api/eod/{symbol}?from={date}&to={date}&period=d&api_token={apiKey}&fmt=json
     */
    fun getPrice(
        symbol: String,
        date: String,
        apiKey: String = DEMO_KEY
    ): List<EodhdPrice> =
        restClient
            .get()
            .uri(
                "/api/eod/{symbol}?from={date}&to={date}&period=d&api_token={apiKey}&fmt=json",
                symbol,
                date,
                date,
                apiKey
            ).retrieve()
            .body<Array<EodhdPrice>>()
            ?.toList()
            ?: emptyList()

    /**
     * Bulk last-day EOD prices for multiple symbols on a single exchange.
     *
     * GET /api/eod-bulk-last-day/{exchange}?symbols={csv}&date={date}&api_token={apiKey}&fmt=json
     *
     * `symbols` is a comma-separated list of raw codes (the EXCHANGE in the path is the default
     * suffix). One HTTP round-trip replaces N per-symbol [getPrice] calls for the scheduled
     * portfolio-valuation fan-out; per-symbol quota is identical (1 API call per ticker) but
     * wall-clock collapses from N × ~1s to 1 × ~1s.
     */
    fun getBulkPrices(
        exchange: String,
        symbols: String,
        date: String,
        apiKey: String = DEMO_KEY
    ): List<EodhdBulkPrice> =
        restClient
            .get()
            .uri(
                "/api/eod-bulk-last-day/{exchange}?symbols={symbols}&date={date}&api_token={apiKey}&fmt=json",
                exchange,
                symbols,
                date,
                apiKey
            ).retrieve()
            .body<Array<EodhdBulkPrice>>()
            ?.toList()
            ?: emptyList()

    /**
     * Historical EOD prices for backfill.
     *
     * GET /api/eod/{symbol}?from={dateFrom}&to={dateTo}&period=d&api_token={apiKey}&fmt=json
     */
    fun getHistory(
        symbol: String,
        dateFrom: String,
        dateTo: String,
        apiKey: String = DEMO_KEY
    ): List<EodhdPrice> =
        restClient
            .get()
            .uri(
                "/api/eod/{symbol}?from={dateFrom}&to={dateTo}&period=d&api_token={apiKey}&fmt=json",
                symbol,
                dateFrom,
                dateTo,
                apiKey
            ).retrieve()
            .body<Array<EodhdPrice>>()
            ?.toList()
            ?: emptyList()

    /**
     * Full dividend history for a symbol.
     *
     * GET /api/div/{symbol}?api_token={apiKey}&fmt=json
     */
    fun getDividends(
        symbol: String,
        apiKey: String = DEMO_KEY
    ): List<EodhdDividend> =
        restClient
            .get()
            .uri("/api/div/{symbol}?api_token={apiKey}&fmt=json", symbol, apiKey)
            .retrieve()
            .body<Array<EodhdDividend>>()
            ?.toList()
            ?: emptyList()

    /**
     * Full split history for a symbol.
     *
     * GET /api/splits/{symbol}?api_token={apiKey}&fmt=json
     */
    fun getSplits(
        symbol: String,
        apiKey: String = DEMO_KEY
    ): List<EodhdSplit> =
        restClient
            .get()
            .uri("/api/splits/{symbol}?api_token={apiKey}&fmt=json", symbol, apiKey)
            .retrieve()
            .body<Array<EodhdSplit>>()
            ?.toList()
            ?: emptyList()

    /**
     * News articles tagged with the given symbol.
     *
     * GET /api/news?api_token={apiKey}&s={symbol}&limit={limit}&from={from}&fmt=json
     *
     * `from` is optional (ISO date or `yyyy-MM-dd`). When omitted EODHD returns the most recent
     * articles regardless of date. EODHD's free tier exposes a roughly 2-day window and a 1200
     * request/day quota that's shared with the rest of the free endpoints.
     */
    fun getNews(
        symbol: String,
        limit: Int = 50,
        from: String? = null,
        apiKey: String = DEMO_KEY
    ): List<EodhdNewsArticle> {
        // Two URI templates instead of string-interpolating the optional clause — keeps each
        // placeholder going through RestClient's URI encoding rather than concatenation.
        val (uri, vars) =
            if (from.isNullOrBlank()) {
                "/api/news?api_token={apiKey}&s={symbol}&limit={limit}&fmt=json" to
                    arrayOf<Any>(apiKey, symbol, limit)
            } else {
                "/api/news?api_token={apiKey}&s={symbol}&limit={limit}&from={from}&fmt=json" to
                    arrayOf<Any>(apiKey, symbol, limit, from)
            }
        return restClient
            .get()
            .uri(uri, *vars)
            .retrieve()
            .body<Array<EodhdNewsArticle>>()
            ?.toList()
            ?: emptyList()
    }

    /**
     * Asset search by ticker code or name fragment.
     *
     * GET /api/search/{query}?api_token={apiKey}&fmt=json
     *
     * EODHD returns a JSON array of asset descriptors. Used by [EodhdPriceService.searchAssets]
     * so the asset-lookup UI surfaces tickers EODHD will go on to price.
     */
    fun searchAssets(
        query: String,
        apiKey: String = DEMO_KEY
    ): List<EodhdSearchResult> =
        searchRestClient
            .get()
            .uri("/api/search/{query}?api_token={apiKey}&fmt=json", query, apiKey)
            .retrieve()
            .body<Array<EodhdSearchResult>>()
            ?.toList()
            ?: emptyList()

    /**
     * ETF/equity fundamentals, projected to the classification fields.
     *
     * GET /api/fundamentals/{symbol}?filter=General,ETF_Data&api_token={apiKey}&fmt=json
     *
     * `filter` trims the payload to the two blocks the classification enricher reads (it does not
     * reduce the per-request API-call cost, which is fixed). Requires a plan with fundamentals
     * access; the `demo` token serves a small whitelist (AAPL, TSLA, VTI, MCD…) for scaffolding.
     */
    fun getFundamentals(
        symbol: String,
        apiKey: String = DEMO_KEY
    ): EodhdFundamentals =
        restClient
            .get()
            .uri(
                "/api/fundamentals/{symbol}?filter=General,ETF_Data&api_token={apiKey}&fmt=json",
                symbol,
                apiKey
            ).retrieve()
            .body<EodhdFundamentals>()
            ?: EodhdFundamentals()

    companion object {
        const val DEMO_KEY = "demo"
    }
}