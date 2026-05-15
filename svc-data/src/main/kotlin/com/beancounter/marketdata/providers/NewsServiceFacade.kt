package com.beancounter.marketdata.providers

import com.beancounter.marketdata.providers.alpha.AlphaNewsService
import com.beancounter.marketdata.providers.eodhd.EodhdNewsService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Routes news lookups to the configured provider.
 *
 * `beancounter.market.providers.news` selects the active backend:
 *  - `alpha`  (default) — AlphaVantage NEWS_SENTIMENT
 *  - `eodhd`           — EODHD `/api/news`
 *
 * Defaulting to `alpha` keeps existing behaviour untouched on upgrade. EODHD's free tier exposes
 * the news endpoint at full coverage so operators on the free EOD plan can flip the flag to gain
 * news without paying for AV premium. Rule-of-three doesn't apply yet — only two providers — so
 * dispatch is a simple flag, not a registered-bean map.
 */
@Service
class NewsServiceFacade(
    private val alphaNewsService: AlphaNewsService,
    private val eodhdNewsService: EodhdNewsService
) : NewsProvider {
    private val log = LoggerFactory.getLogger(NewsServiceFacade::class.java)

    @Value($$"${beancounter.market.news.provider:alpha}")
    private lateinit var provider: String

    override fun getNewsSentiment(
        tickers: String,
        market: String?,
        topics: String?
    ): Map<String, Any> = activeProvider().getNewsSentiment(tickers, market, topics)

    private fun activeProvider(): NewsProvider =
        when (provider.lowercase()) {
            "eodhd" -> {
                eodhdNewsService
            }
            "alpha" -> {
                alphaNewsService
            }
            else -> {
                log.warn("Unknown news provider '{}'; falling back to alpha", provider)
                alphaNewsService
            }
        }
}