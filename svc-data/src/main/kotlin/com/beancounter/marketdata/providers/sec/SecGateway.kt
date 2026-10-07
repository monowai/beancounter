package com.beancounter.marketdata.providers.sec

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Thin RestClient wrapper for SEC EDGAR's public JSON endpoints. Keyless; the mandatory
 * `User-Agent` is a default header on both clients (see `ExternalApiRestClientConfig`).
 *
 * Bodies are returned raw: the payloads are large and loosely shaped (XBRL facts keyed by tag),
 * so the callers parse the slice they need rather than binding the whole document. SEC always
 * answers these paths with a JSON document, so an empty body is an error worth naming rather
 * than an end-of-input parse failure further along.
 */
@Component
class SecGateway(
    @Qualifier("secRestClient")
    private val restClient: RestClient,
    @Qualifier("secTickersRestClient")
    private val tickersRestClient: RestClient
) {
    /** GET https://www.sec.gov/files/company_tickers.json - the ticker -> CIK index. */
    fun getCompanyTickers(): String =
        tickersRestClient
            .get()
            .uri(TICKERS_PATH)
            .retrieve()
            .bodyOrFail(TICKERS_PATH)

    /** GET https://data.sec.gov/submissions/CIK{cik10}.json - registrant profile incl. SIC. */
    fun getSubmissions(cik10: String): String =
        restClient
            .get()
            .uri("/submissions/CIK{cik}.json", cik10)
            .retrieve()
            .bodyOrFail("/submissions/CIK$cik10.json")

    /** GET https://data.sec.gov/api/xbrl/companyfacts/CIK{cik10}.json - all XBRL facts. */
    fun getCompanyFacts(cik10: String): String =
        restClient
            .get()
            .uri("/api/xbrl/companyfacts/CIK{cik}.json", cik10)
            .retrieve()
            .bodyOrFail("/api/xbrl/companyfacts/CIK$cik10.json")

    private fun RestClient.ResponseSpec.bodyOrFail(path: String): String {
        val body = body<String>()
        require(!body.isNullOrBlank()) { "SEC returned an empty body for $path" }
        return body
    }

    companion object {
        private const val TICKERS_PATH = "/files/company_tickers.json"
    }
}