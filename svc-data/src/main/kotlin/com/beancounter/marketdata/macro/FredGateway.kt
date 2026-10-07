package com.beancounter.marketdata.macro

import io.github.resilience4j.retry.annotation.Retry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Thin RestClient wrapper for FRED's keyless `fredgraph.csv` download. No API key — the St Louis
 * Fed publishes these series as public-domain data.
 */
@Component
class FredGateway(
    @Qualifier("fredRestClient")
    private val restClient: RestClient
) {
    /**
     * Full observation history for one FRED series as CSV, oldest first.
     *
     * GET /graph/fredgraph.csv?id={seriesId}
     *
     * Body is `observation_date,{seriesId}` followed by one `yyyy-MM-dd,value` row per calendar
     * day; non-trading days carry `.` as the value.
     */
    @Retry(name = "providerHttp")
    fun getSeriesCsv(seriesId: String): String =
        restClient
            .get()
            .uri("/graph/fredgraph.csv?id={seriesId}", seriesId)
            .retrieve()
            .body(String::class.java)
            .orEmpty()
}