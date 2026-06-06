package com.beancounter.marketdata.providers.alpha

import com.beancounter.auth.model.AuthConstants
import com.beancounter.marketdata.providers.NewsServiceFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * News and sentiment data endpoint.
 *
 * Delegates to [NewsServiceFacade] which routes by `beancounter.market.providers.news`
 * (default `alpha`, optional `eodhd`).
 */
@RestController
@RequestMapping("/news")
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(name = "News", description = "Financial news and sentiment analysis")
class AlphaNewsController(
    private val newsService: NewsServiceFacade
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get news and sentiment for tickers",
        description =
            "Retrieves recent news articles with per-ticker sentiment from the configured provider " +
                "(AlphaVantage or EODHD)."
    )
    fun getNews(
        @Parameter(description = "Comma-separated ticker symbols", example = "AAPL,MSFT")
        @RequestParam tickers: String,
        @Parameter(description = "Market code for non-US exchanges (e.g. NZX, ASX, LON)", required = false)
        @RequestParam(required = false) market: String? = null,
        @Parameter(description = "Optional topic filter", required = false)
        @RequestParam(required = false) topics: String? = null
    ): Map<String, Any> = newsService.getNewsSentiment(tickers, market, topics)

    @GetMapping("/market", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get market / sector news by proxy symbols",
        description =
            "Fetches news for verbatim proxy symbols — an index (e.g. GSPC.INDX) for market-wide " +
                "macro moves, or a sector ETF (e.g. XLK.US) for sector-level moves. Used to surface " +
                "context that per-holding news misses. Only the EODHD provider has index/ETF news " +
                "coverage; other providers return no coverage."
    )
    fun getMarketNews(
        @Parameter(description = "Comma-separated EODHD proxy symbols", example = "GSPC.INDX,XLK.US")
        @RequestParam symbols: String,
        @Parameter(description = "Optional topic filter", required = false)
        @RequestParam(required = false) topics: String? = null
    ): Map<String, Any> = newsService.getMarketNews(symbols.split(","), topics)
}