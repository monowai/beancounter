package com.beancounter.marketdata.providers.eodhd

import com.beancounter.marketdata.providers.eodhd.model.EodhdDividend
import com.beancounter.marketdata.providers.eodhd.model.EodhdPrice
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
    private val restClient: RestClient
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

    companion object {
        const val DEMO_KEY = "demo"
    }
}