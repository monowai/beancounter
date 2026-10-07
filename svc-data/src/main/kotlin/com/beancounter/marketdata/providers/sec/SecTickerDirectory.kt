package com.beancounter.marketdata.providers.sec

import com.beancounter.common.utils.BcJson
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Parses SEC `company_tickers.json` into a ticker -> ten-digit CIK map, cached for a day.
 *
 * Lives apart from [SecTickerResolver] because `@Cacheable` only intercepts calls that cross a
 * bean boundary - a resolver caching its own helper would re-download the index per asset.
 *
 * Shape: `{"0":{"cik_str":320193,"ticker":"AAPL","title":"Apple Inc."},...}` - keyed by row
 * index, so the index keys are discarded and the `ticker` field becomes the map key.
 */
@Component
class SecTickerDirectory(
    private val secProxy: SecProxy,
    private val objectMapper: ObjectMapper = BcJson.objectMapper
) {
    @Cacheable("sec.tickers")
    fun tickerToCik(): Map<String, String> =
        objectMapper
            .readTree(secProxy.getCompanyTickers())
            .properties()
            .mapNotNull { (_, row) ->
                val ticker =
                    row
                        .path("ticker")
                        .asString()
                        .trim()
                        .uppercase()
                val rawCik = row.path("cik_str").asString().trim()
                // Filter on the raw value: padding a blank would mint the bogus CIK 0000000000.
                if (ticker.isBlank() || rawCik.isBlank()) null else ticker to rawCik.padStart(CIK_LENGTH, '0')
            }.toMap()

    companion object {
        const val CIK_LENGTH = 10
    }
}