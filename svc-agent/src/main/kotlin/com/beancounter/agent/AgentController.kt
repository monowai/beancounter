package com.beancounter.agent

import com.beancounter.auth.model.AuthConstants
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST Controller for Beancounter AI Agent
 *
 * Provides endpoints for AI agent interactions including natural language queries,
 * portfolio analysis, and market overviews.
 */
@RestController
@RequestMapping("/agent")
@CrossOrigin
@Tag(
    name = "AI Agent",
    description = "Beancounter AI Agent for natural language portfolio and market analysis"
)
class AgentController(
    private val beancounterAgent: BeancounterAgent,
    private val healthService: HealthService
) {
    private val log = LoggerFactory.getLogger(AgentController::class.java)

    companion object {
        private const val LOGIN_REDIRECT = "redirect:/login.html"
        private const val CHAT_REDIRECT = "redirect:/chat.html"
    }

    @GetMapping("/login")
    @Operation(
        summary = "Redirect to login page",
        description = "Redirects to the static login page."
    )
    fun getLoginPage(): String = LOGIN_REDIRECT

    @GetMapping("/chat")
    @Operation(
        summary = "Redirect to chat interface",
        description = "Redirects to the static chat interface."
    )
    fun getChatInterfaceAtPath(): String = CHAT_REDIRECT

    @GetMapping("/")
    @Operation(
        summary = "Redirect to chat interface",
        description = "Redirects to the static chat interface at the root path."
    )
    fun getRoot(): String = CHAT_REDIRECT

    @GetMapping("/health")
    @Operation(
        summary = "Get service health status",
        description = "Get the health status of all MCP services (Data, Event, Position)"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Health status retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Health Status",
                                summary = "Example health status response",
                                value = """
                        {
                          "overallStatus": "GREEN",
                          "services": [
                            {
                              "name": "Data Service",
                              "status": "UP",
                              "responseTime": 45,
                              "lastChecked": "2024-01-15T10:30:00",
                              "error": null
                            },
                            {
                              "name": "Event Service",
                              "status": "UP",
                              "responseTime": 32,
                              "lastChecked": "2024-01-15T10:30:00",
                              "error": null
                            },
                            {
                              "name": "Position Service",
                              "status": "UP",
                              "responseTime": 28,
                              "lastChecked": "2024-01-15T10:30:00",
                              "error": null
                            }
                          ],
                          "lastChecked": "2024-01-15T10:30:00",
                          "summary": "3 of 3 services available"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun getHealthStatus(): ServiceHealthStatus = healthService.checkAllServicesHealth()

    @PostMapping("/query")
    @PreAuthorize(
        "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
    )
    @Operation(
        summary = "Process natural language query",
        description = "Process a natural language query and return structured results with AI-generated response"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Query processed successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Portfolio Analysis Query",
                                value = """
                        {
                          "query": "Show me my portfolio analysis",
                          "response": "Here's your portfolio analysis. I've retrieved your portfolio information and current positions.",
                          "actions": [
                            {
                              "id": "get_portfolio",
                              "type": "GET_PORTFOLIO",
                              "description": "Get portfolio information"
                            }
                          ],
                          "results": {
                            "get_portfolio": {
                              "id": "portfolio-123",
                              "code": "MAIN",
                              "name": "Main Portfolio"
                            }
                          },
                          "timestamp": "2024-01-15"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun processQuery(
        @Parameter(description = "Natural language query to process")
        @RequestBody queryRequest: QueryRequest
    ): AgentResponse {
        val authentication =
            org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .authentication
        log.info(
            "Processing query '{}' with authentication: {} (type: {})",
            queryRequest.query,
            authentication?.name,
            authentication?.javaClass?.simpleName
        )

        return beancounterAgent.processQuery(queryRequest.query, queryRequest.context)
    }

    @PostMapping("/test")
    @Operation(
        summary = "Test endpoint without MCP calls",
        description = "Simple test endpoint that doesn't call external MCP services"
    )
    fun testQuery(
        @RequestBody queryRequest: QueryRequest
    ): AgentResponse =
        AgentResponse(
            query = queryRequest.query,
            response = "Test response for: ${queryRequest.query}",
            actions = emptyList(),
            results = mapOf("test" to "This is a test response without calling MCP services"),
            timestamp = java.time.LocalDate.now()
        )

    @GetMapping("/portfolio/{portfolioId}/analysis")
    @Operation(
        summary = "Get comprehensive portfolio analysis",
        description = "Get detailed analysis of a portfolio including positions, events, and metrics"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Portfolio analysis retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Portfolio Analysis",
                                value = """
                        {
                          "portfolio": {
                            "id": "portfolio-123",
                            "code": "MAIN",
                            "name": "Main Portfolio"
                          },
                          "positions": {
                            "data": {
                              "positions": [
                                {
                                  "asset": {
                                    "id": "asset-456",
                                    "code": "AAPL",
                                    "name": "Apple Inc."
                                  },
                                  "quantity": 100,
                                  "marketValue": 15000.00
                                }
                              ]
                            }
                          },
                          "events": {
                            "data": []
                          },
                          "metrics": {
                            "totalValue": 15000.00,
                            "totalGain": 500.00
                          },
                          "analysisDate": "2024-01-15"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun analyzePortfolio(
        @Parameter(description = "Portfolio identifier")
        @PathVariable portfolioId: String,
        @Parameter(description = "Analysis date in YYYY-MM-DD format or 'today'")
        @RequestParam(defaultValue = "today") date: String
    ): PortfolioAnalysis = beancounterAgent.analyzePortfolio(portfolioId, date)

    @GetMapping("/market/overview")
    @Operation(
        summary = "Get market overview",
        description = "Get comprehensive market overview including markets, currencies, and FX rates"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Market overview retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Market Overview",
                                value = """
                        {
                          "markets": {
                            "data": [
                              {
                                "code": "NYSE",
                                "name": "New York Stock Exchange",
                                "currencyId": "USD"
                              }
                            ]
                          },
                          "currencies": [
                            {
                              "id": "USD",
                              "code": "USD",
                              "name": "US Dollar"
                            }
                          ],
                          "fxRates": {
                            "USD-EUR": {
                              "fromCurrency": "USD",
                              "toCurrency": "EUR",
                              "rate": 0.85
                            }
                          },
                          "timestamp": "2024-01-15"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun getMarketOverview(): MarketOverview = beancounterAgent.getMarketOverview()

    @PostMapping("/portfolio/{portfolioId}/events/load")
    @Operation(
        summary = "Load events for portfolio",
        description = "Load corporate events from external sources for a specific portfolio"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Event loading initiated successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Event Loading Response",
                                value = """
                        {
                          "portfolioId": "portfolio-123",
                          "fromDate": "2024-01-01",
                          "status": "loading_started",
                          "message": "Event loading initiated for portfolio portfolio-123 from 2024-01-01"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun loadEventsForPortfolio(
        @Parameter(description = "Portfolio identifier")
        @PathVariable portfolioId: String,
        @Parameter(description = "Start date in YYYY-MM-DD format or 'today'")
        @RequestParam fromDate: String
    ): Map<String, Any> = beancounterAgent.loadEventsForPortfolio(portfolioId, fromDate)

    @PostMapping("/portfolio/{portfolioId}/events/backfill")
    @Operation(
        summary = "Backfill events for portfolio",
        description = "Backfill and reprocess existing corporate events for a portfolio"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Event backfilling initiated successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Event Backfill Response",
                                value = """
                        {
                          "portfolioId": "portfolio-123",
                          "fromDate": "2024-01-01",
                          "toDate": "2024-01-15",
                          "status": "backfill_started",
                          "message": "Event backfilling initiated for portfolio portfolio-123 from 2024-01-01 to 2024-01-15"
                        }
                        """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun backfillEventsForPortfolio(
        @Parameter(description = "Portfolio identifier")
        @PathVariable portfolioId: String,
        @Parameter(description = "Start date in YYYY-MM-DD format or 'today'")
        @RequestParam fromDate: String,
        @Parameter(description = "End date in YYYY-MM-DD format (optional)")
        @RequestParam(required = false) toDate: String?
    ): Map<String, Any> = beancounterAgent.backfillEvents(portfolioId, fromDate, toDate)

    @GetMapping("/capabilities")
    @Operation(
        summary = "Get agent capabilities",
        description = "Get information about what the agent can do"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Agent capabilities retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Agent Capabilities",
                                value = """
                        {
                          "name": "Beancounter AI Agent",
                          "version": "1.0.0",
                          "capabilities": [
                            "Portfolio analysis and management",
                            "Market data retrieval and analysis",
                            "Corporate event processing",
                            "Position valuation and reporting",
                            "Natural language query processing",
                            "FX rate monitoring",
                            "Multi-service orchestration"
                          ],
                          "supportedQueries": [
                            "Show me my portfolio analysis",
                            "What's the market overview?",
                            "Load events for my portfolio",
                            "Get current positions",
                            "What are the FX rates?"
                          ],
                          "mcpServices": [
                            {
                              "name": "Data Service",
                              "url": "http://localhost:9510/api/mcp",
                              "capabilities": ["Assets", "Portfolios", "Market Data", "FX Rates"]
                            },
                            {
                              "name": "Event Service",
                              "url": "http://localhost:9520/api/mcp",
                              "capabilities": ["Corporate Events", "Event Loading", "Backfilling"]
                            },
                            {
                              "name": "Position Service",
                              "url": "http://localhost:9500/api/mcp",
                              "capabilities": ["Positions", "Valuations", "Metrics"]
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
    fun getCapabilities(): Map<String, Any> =
        mapOf(
            "name" to "Beancounter AI Agent",
            "version" to "1.0.0",
            "capabilities" to
                listOf(
                    "Portfolio analysis and management",
                    "Market data retrieval and analysis",
                    "Corporate event processing",
                    "Position valuation and reporting",
                    "Natural language query processing",
                    "FX rate monitoring",
                    "Multi-service orchestration"
                ),
            "supportedQueries" to
                listOf(
                    "Show me my portfolio analysis",
                    "What's the market overview?",
                    "Load events for my portfolio",
                    "Get current positions",
                    "What are the FX rates?"
                ),
            "mcpServices" to
                listOf(
                    mapOf(
                        "name" to "Data Service",
                        "url" to "http://localhost:9510/api/mcp",
                        "capabilities" to listOf("Assets", "Portfolios", "Market Data", "FX Rates")
                    ),
                    mapOf(
                        "name" to "Event Service",
                        "url" to "http://localhost:9520/api/mcp",
                        "capabilities" to listOf("Corporate Events", "Event Loading", "Backfilling")
                    ),
                    mapOf(
                        "name" to "Position Service",
                        "url" to "http://localhost:9500/api/mcp",
                        "capabilities" to listOf("Positions", "Valuations", "Metrics")
                    )
                )
        )

    @GetMapping("/debug/ai-status")
    @Operation(
        summary = "Debug AI status",
        description = "Check if SpringAI is available and configured"
    )
    fun getAiStatus(): Map<String, Any> = beancounterAgent.getAiStatus()
}

/**
 * Request object for natural language queries
 */
data class QueryRequest(
    val query: String,
    val context: Map<String, Any> = emptyMap()
)