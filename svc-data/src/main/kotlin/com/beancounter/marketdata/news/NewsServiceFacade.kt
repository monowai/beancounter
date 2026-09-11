package com.beancounter.marketdata.news

import com.beancounter.marketdata.news.alpha.AlphaNewsService
import com.beancounter.marketdata.news.eodhd.EodhdNewsService
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
 *
 * Extraction seams, should `com.beancounter.marketdata.news` become its own service: (a)
 * [com.beancounter.marketdata.news.eodhd.EodhdNewsService]'s call into `AssetFinder` for
 * priceSymbol resolution would become an assets-API call; (b) the Alpha/EODHD gateways this
 * package's providers wrap are shared with price/event flows, so a standalone news service would
 * carry its own thin gateway clients rather than take the shared ones; (c) `marketdata.macro`
 * consumes news only through this facade's public [NewsProvider] surface, so it would keep working
 * unchanged against a remote news service behind the same interface.
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

    override fun getMarketNews(
        symbols: List<String>,
        topics: String?
    ): Map<String, Any> = activeProvider().getMarketNews(symbols, topics)

    override fun getTopicNews(topics: List<String>): Map<String, Any> = activeProvider().getTopicNews(topics)

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