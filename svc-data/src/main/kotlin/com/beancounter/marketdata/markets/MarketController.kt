package com.beancounter.marketdata.markets

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.MarketResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Market Data MVC for market information operations.
 * Provides endpoints for retrieving information about financial markets and exchanges.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/markets")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Market Information",
    description = "Operations for retrieving information about financial markets and exchanges"
)
class MarketController internal constructor(
    private val marketService: MarketService
) {
    @GetMapping
    @Operation(
        summary = "Get all markets",
        description = """
            Retrieves information about all available financial markets and exchanges.
            
            Use this to:
            * List all supported markets
            * Get market overview for display
            * Check available trading venues
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Markets retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Market List",
                                value = """
                                {
                                  "data": [
                                    {
                                      "code": "NYSE",
                                      "name": "New York Stock Exchange",
                                      "country": "US",
                                      "currency": "USD"
                                    },
                                    {
                                      "code": "NASDAQ",
                                      "name": "NASDAQ Stock Market",
                                      "country": "US",
                                      "currency": "USD"
                                    }
                                  ]
                                }
                                """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun getMarkets(): MarketResponse = marketService.getMarkets()

    @GetMapping(
        value = ["/{code}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get market by code",
        description = """
            Retrieves detailed information about a specific market by its code.
            
            Use this to:
            * Get market details for a specific exchange
            * View market configuration and settings
            * Access market metadata
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Market retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Market not found"
            )
        ]
    )
    fun getMarket(
        @Parameter(
            description = "Market code (e.g., NYSE, NASDAQ, ASX)",
            example = "NYSE"
        )
        @PathVariable code: String
    ): MarketResponse = MarketResponse(setOf(marketService.getMarket(code)))
}