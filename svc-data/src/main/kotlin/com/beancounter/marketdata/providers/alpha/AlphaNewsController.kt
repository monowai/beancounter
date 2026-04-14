package com.beancounter.marketdata.providers.alpha

import com.beancounter.auth.model.AuthConstants
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
 */
@RestController
@RequestMapping("/news")
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(name = "News", description = "Financial news and sentiment analysis")
class AlphaNewsController(
    private val newsService: AlphaNewsService
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get news and sentiment for tickers",
        description = "Retrieves recent news articles with per-ticker sentiment from Alpha Vantage."
    )
    fun getNews(
        @Parameter(description = "Comma-separated ticker symbols", example = "AAPL,MSFT")
        @RequestParam tickers: String,
        @Parameter(description = "Optional topic filter", required = false)
        @RequestParam(required = false) topics: String? = null
    ): Map<String, Any> = newsService.getNewsSentiment(tickers, topics)
}