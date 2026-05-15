package com.beancounter.marketdata.providers.eodhd

import com.beancounter.marketdata.providers.eodhd.model.EodhdPrice
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

    companion object {
        const val DEMO_KEY = "demo"
    }
}