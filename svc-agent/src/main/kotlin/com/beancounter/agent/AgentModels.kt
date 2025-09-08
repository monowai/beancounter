package com.beancounter.agent

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Portfolio
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.Locale

// Note: Event contracts are in svc-event module, using generic types for now

/**
 * Response from the Beancounter Agent
 */
data class AgentResponse(
    val query: String,
    val response: String,
    val actions: List<AgentAction>,
    val results: Map<String, Any>,
    val timestamp: LocalDate,
    val error: String? = null
)

/**
 * Action that the agent can perform
 */
data class AgentAction(
    val id: String,
    val type: ActionType,
    val parameters: Map<String, Any>,
    val description: String
)

/**
 * Types of actions the agent can perform
 */
enum class ActionType {
    GET_PORTFOLIO,
    GET_PORTFOLIOS,
    GET_POSITIONS,
    GET_EVENTS,
    GET_MARKET_DATA,
    GET_FX_RATES,
    LOAD_EVENTS,
    BACKFILL_EVENTS,
    GET_MARKETS,
    GET_CURRENCIES,
    QUERY_POSITIONS,
    BUILD_POSITIONS,
    VALUE_POSITIONS,
    GET_METRICS,
    GET_BREAKDOWN,
    VERIFY_CONNECTIVITY
}

/**
 * Analysis of a portfolio including positions, events, and metrics
 */
data class PortfolioAnalysis(
    val portfolio: Portfolio,
    val positions: PositionResponse,
    val events: Map<String, Any>,
    val metrics: Map<String, Any>,
    val analysisDate: String
)

/**
 * Market overview with key market data
 */
data class MarketOverview(
    val markets: com.beancounter.common.contracts.MarketResponse,
    val currencies: List<com.beancounter.common.model.Currency>,
    val fxRates: Map<String, com.beancounter.common.contracts.FxResponse>,
    val timestamp: LocalDate
)

/**
 * Query analysis result from LLM
 */
data class QueryAnalysis(
    val intent: String,
    val entities: Map<String, String>,
    val actions: List<AgentAction>,
    val confidence: Double
)

/**
 * LLM Service interface for natural language processing
 */
interface LlmService {
    /**
     * Analyze a natural language query and determine required actions
     */
    fun analyzeQuery(
        query: String,
        context: Map<String, Any>
    ): QueryAnalysis

    /**
     * Generate a natural language response based on query and results
     */
    fun generateResponse(
        originalQuery: String,
        results: Map<String, Any>,
        analysis: QueryAnalysis
    ): String
}

/**
 * Simple LLM Service implementation for basic query processing
 */
class SimpleLlmService : LlmService {
    private val log = LoggerFactory.getLogger(SimpleLlmService::class.java)

    private val systemPrompt: String by lazy {
        try {
            javaClass.getResourceAsStream("/prompts/system-prompt.md")?.use { inputStream ->
                inputStream.bufferedReader().readText()
            }
                ?: (
                    "You are the Beancounter AI Agent. " +
                        "Help users with portfolio analysis, market data, and corporate events."
                )
        } catch (e: RuntimeException) {
            log.warn("Failed to load system prompt from file, using default", e)
            "You are the Beancounter AI Agent. " +
                "Help users with portfolio analysis, market data, and corporate events."
        }
    }

    override fun analyzeQuery(
        query: String,
        context: Map<String, Any>
    ): QueryAnalysis {
        val lowerQuery = query.lowercase()

        val actions = mutableListOf<AgentAction>()
        val entities = mutableMapOf<String, String>()

        // Simple keyword-based analysis
        when {
            lowerQuery.contains("portfolio") && (lowerQuery.contains("analysis") || lowerQuery.contains("analyze")) -> {
                val portfolioCode = extractPortfolioCode(query) ?: "default"
                entities["portfolioCode"] = portfolioCode
                actions.add(
                    AgentAction(
                        id = "get_portfolio",
                        type = ActionType.GET_PORTFOLIO,
                        parameters = mapOf("portfolioCode" to portfolioCode),
                        description = "Get portfolio information"
                    )
                )
                actions.add(
                    AgentAction(
                        id = "get_positions",
                        type = ActionType.GET_POSITIONS,
                        parameters =
                            mapOf(
                                "portfolio" to "placeholder", // Will be replaced with actual portfolio
                                "date" to "today"
                            ),
                        description = "Get portfolio positions"
                    )
                )
            }

            lowerQuery.contains("market") && lowerQuery.contains("overview") -> {
                actions.add(
                    AgentAction(
                        id = "get_markets",
                        type = ActionType.GET_MARKETS,
                        parameters = emptyMap(),
                        description = "Get market information"
                    )
                )
                actions.add(
                    AgentAction(
                        id = "get_currencies",
                        type = ActionType.GET_CURRENCIES,
                        parameters = emptyMap(),
                        description = "Get currency information"
                    )
                )
            }

            lowerQuery.contains("market") && lowerQuery.contains("data") -> {
                val assetCode = extractAssetCode(query)
                if (assetCode != null) {
                    actions.add(
                        AgentAction(
                            id = "get_market_data",
                            type = ActionType.GET_MARKET_DATA,
                            parameters =
                                mapOf(
                                    "assetCode" to assetCode, // Using asset code instead of UUID
                                    "date" to "today"
                                ),
                            description = "Get market data for $assetCode"
                        )
                    )
                } else {
                    // If no specific symbol, get general market data
                    actions.add(
                        AgentAction(
                            id = "get_markets",
                            type = ActionType.GET_MARKETS,
                            parameters = emptyMap(),
                            description = "Get market information"
                        )
                    )
                }
            }

            lowerQuery.contains("events") && lowerQuery.contains("load") -> {
                val portfolioCode = extractPortfolioCode(query) ?: "default"
                entities["portfolioCode"] = portfolioCode
                actions.add(
                    AgentAction(
                        id = "load_events",
                        type = ActionType.LOAD_EVENTS,
                        parameters =
                            mapOf(
                                "portfolioCode" to portfolioCode,
                                "fromDate" to "today"
                            ),
                        description = "Load corporate events for portfolio"
                    )
                )
            }

            lowerQuery.contains("list") && lowerQuery.contains("portfolio") -> {
                actions.add(
                    AgentAction(
                        id = "get_portfolios",
                        type = ActionType.GET_PORTFOLIOS,
                        parameters = emptyMap(),
                        description = "Get all portfolios"
                    )
                )
            }

            lowerQuery.contains("connectivity") ||
                (
                    lowerQuery.contains("service") &&
                        (
                            lowerQuery.contains("status") ||
                                lowerQuery.contains(
                                    "health"
                                ) ||
                                lowerQuery.contains("ping")
                        )
                ) -> {
                actions.add(
                    AgentAction(
                        id = "verify_connectivity",
                        type = ActionType.VERIFY_CONNECTIVITY,
                        parameters = emptyMap(),
                        description = "Verify connectivity to all MCP services"
                    )
                )
            }

            else -> {
                // Default action - get all portfolios
                actions.add(
                    AgentAction(
                        id = "get_portfolios",
                        type = ActionType.GET_PORTFOLIOS,
                        parameters = emptyMap(),
                        description = "Get all portfolios"
                    )
                )
            }
        }

        return QueryAnalysis(
            intent = determineIntent(lowerQuery),
            entities = entities,
            actions = actions,
            confidence = 0.8 // Simple implementation assumes high confidence
        )
    }

    override fun generateResponse(
        originalQuery: String,
        results: Map<String, Any>,
        analysis: QueryAnalysis
    ): String =
        when (analysis.intent) {
            "portfolio_analysis" -> {
                generatePortfolioAnalysisResponse(results)
            }
            "list_portfolios" -> {
                generateListPortfoliosResponse(results)
            }
            "verify_connectivity" -> {
                generateConnectivityResponse(results)
            }
            "market_overview" -> {
                generateMarketOverviewResponse(results)
            }
            "load_events" -> {
                "I've initiated loading of corporate events for your portfolio. " +
                    "This process will gather all relevant corporate actions that might affect your holdings."
            }
            "get_positions" -> {
                generatePositionsResponse(results)
            }
            "market_data" -> {
                generateMarketDataResponse(results)
            }
            else -> {
                generateGeneralResponse(originalQuery, results, analysis)
            }
        }

    private fun generateListPortfoliosResponse(results: Map<String, Any>): String {
        val portfoliosResponse = results["get_portfolios"] as? com.beancounter.common.contracts.PortfoliosResponse

        return buildString {
            appendLine("📋 **Your Portfolios**")
            appendLine()

            if (portfoliosResponse != null && portfoliosResponse.data.isNotEmpty()) {
                // Create a table format
                appendLine("| Portfolio Name | ID | Code | Base Currency | Market Value | IRR |")
                appendLine("|---|---|---|---|---|---|")

                portfoliosResponse.data.forEach { portfolio ->
                    val marketValue = portfolio.marketValue.let { "$${String.format(Locale.US, "%.2f", it)}" } ?: "N/A"
                    val irr = portfolio.irr.let { "${String.format(Locale.US, "%.2f", it)}%" }

                    appendLine(
                        "| ${portfolio.name} | ${portfolio.id} | ${portfolio.code} | ${portfolio.base.code} | $marketValue | $irr |"
                    )
                }

                appendLine()
                appendLine("**Summary:** ${portfoliosResponse.data.size} portfolio(s) found")
                appendLine()
                appendLine("💡 **What you can do:**")
                appendLine("• Ask for analysis of a specific portfolio")
                appendLine("• Get positions for any portfolio")
                appendLine("• Load events for a portfolio")
                appendLine("• Example: \"Analyze portfolio ${portfoliosResponse.data.first().id}\"")
            } else {
                appendLine("No portfolios found. This might be because:")
                appendLine("• You don't have access to any portfolios")
                appendLine("• The portfolio service is not responding")
                appendLine("• There was an authentication issue")
                appendLine()
                appendLine("💡 **Try:**")
                appendLine("• Check your authentication token")
                appendLine("• Verify the portfolio service is running")
                appendLine("• Contact your administrator for portfolio access")
            }
        }
    }

    private fun generateConnectivityResponse(results: Map<String, Any>): String {
        val connectivityResult = results["verify_connectivity"] as? ServiceHealthStatus

        return buildString {
            appendLine("🔍 **Service Connectivity Report**")
            appendLine()

            if (connectivityResult != null) {
                appendLine(
                    "**Overall Status:** ${
                        getStatusEmoji(
                            connectivityResult.overallStatus
                        )
                    } ${connectivityResult.overallStatus}"
                )
                appendLine("**Summary:** ${connectivityResult.summary}")
                appendLine("**Last Checked:** ${connectivityResult.lastChecked}")
                appendLine()

                appendLine("**Individual Service Status:**")
                connectivityResult.services.forEach { service ->
                    val statusEmoji =
                        when (service.status) {
                            "UP" -> "🟢"
                            "AMBER" -> "🟡"
                            "DOWN" -> "🔴"
                            else -> "⚪"
                        }

                    appendLine("$statusEmoji **${service.name}**")
                    appendLine("   - Status: ${service.status}")
                    appendLine("   - Response Time: ${service.responseTime}ms")
                    appendLine("   - Last Checked: ${service.lastChecked}")
                    if (service.error != null) {
                        appendLine("   - Error: ${service.error}")
                    }
                    appendLine()
                }

                // Add troubleshooting tips
                appendLine("**Troubleshooting Tips:**")
                val downServices = connectivityResult.services.filter { it.status == "DOWN" }
                val amberServices = connectivityResult.services.filter { it.status == "AMBER" }

                if (downServices.isNotEmpty()) {
                    appendLine("🔴 **Down Services:**")
                    downServices.forEach { service ->
                        appendLine("- ${service.name}: ${service.error ?: "Unknown error"}")
                    }
                    appendLine()
                }

                if (amberServices.isNotEmpty()) {
                    appendLine("🟡 **Partially Available Services:**")
                    amberServices.forEach { service ->
                        appendLine("- ${service.name}: ${service.error ?: "Partial functionality"}")
                    }
                    appendLine()
                }

                if (connectivityResult.overallStatus == "GREEN") {
                    appendLine("✅ **All services are operational!** You can proceed with portfolio queries.")
                } else {
                    appendLine("⚠️ **Some services are not fully operational.** This may affect certain features.")
                }
            } else {
                appendLine("❌ **Unable to verify connectivity** - No connectivity data available.")
            }
        }
    }

    private fun getStatusEmoji(status: String): String =
        when (status) {
            "GREEN" -> "🟢"
            "AMBER" -> "🟡"
            "RED" -> "🔴"
            else -> "⚪"
        }

    private fun generatePortfolioAnalysisResponse(results: Map<String, Any>): String {
        val portfolio = results["get_portfolio"] as? Portfolio
        val positions = results["get_positions"] as? PositionResponse

        return buildString {
            appendLine("📊 **Portfolio Analysis Complete**")
            appendLine()

            if (portfolio != null) {
                appendLine("**Portfolio:** ${portfolio.name} (${portfolio.id})")
                appendLine("**Base Currency:** ${portfolio.base.code}")
                appendLine()
            }

            if (positions != null && positions.data.positions.isNotEmpty()) {
                appendLine("**Current Positions:** ${positions.data.positions.size} holdings")
                appendLine()

                // Show top positions by asset code
                val sortedPositions =
                    positions.data.positions.values
                        .sortedBy { it.asset.code }
                        .take(5)

                appendLine("**Top Holdings:**")
                sortedPositions.forEach { position ->
                    appendLine("• ${position.asset.code}: ${position.asset.name}")
                }
                appendLine()
            }

            appendLine("💡 **Suggestions:**")
            appendLine("• Ask me about specific assets for detailed analysis")
            appendLine("• Request market data for any of your holdings")
            appendLine("• Check for upcoming corporate events")
        }
    }

    private fun generateMarketOverviewResponse(results: Map<String, Any>): String {
        val markets = results["get_markets"] as? com.beancounter.common.contracts.MarketResponse
        val currencies = results["get_currencies"] as? List<com.beancounter.common.model.Currency>

        return buildString {
            appendLine("🌍 **Market Overview**")
            appendLine()

            if (markets != null && !markets.data.isNullOrEmpty()) {
                val marketList = markets.data!!
                appendLine("**Available Markets:** ${marketList.size}")
                marketList.take(5).forEach { market ->
                    appendLine("• ${market.code} - ${market.currency.code}")
                }
                appendLine()
            }

            if (currencies != null && currencies.isNotEmpty()) {
                appendLine("**Supported Currencies:** ${currencies.size}")
                currencies.take(10).forEach { currency ->
                    appendLine("• ${currency.name} (${currency.code}) ${currency.symbol}")
                }
                appendLine()
            }

            appendLine("💡 **Next Steps:**")
            appendLine("• Ask for specific market data: 'Get market data for AAPL'")
            appendLine("• Check FX rates: 'Get USD to EUR exchange rate'")
            appendLine("• Analyze your portfolio against these markets")
        }
    }

    private fun generatePositionsResponse(results: Map<String, Any>): String {
        val positions = results["get_positions"] as? PositionResponse

        return if (positions != null && positions.data.positions.isNotEmpty()) {
            buildString {
                appendLine("📈 **Your Current Positions**")
                appendLine()
                appendLine("**Total Holdings:** ${positions.data.positions.size}")
                appendLine()

                positions.data.positions.values.take(10).forEach { position ->
                    appendLine("• **${position.asset.code}**: ${position.asset.name}")
                }

                if (positions.data.positions.size > 10) {
                    appendLine("... and ${positions.data.positions.size - 10} more")
                }
            }
        } else {
            "No positions found. This might be because the portfolio is empty or there was an issue retrieving the data."
        }
    }

    private fun generateMarketDataResponse(results: Map<String, Any>): String {
        val marketData = results["get_market_data"] as? Map<String, Any>

        return if (marketData != null && marketData.isNotEmpty()) {
            buildString {
                appendLine("📊 **Market Data**")
                appendLine()
                marketData.forEach { (key, value) ->
                    appendLine("**${key.replaceFirstChar { it.uppercase() }}:** $value")
                }
            }
        } else {
            "Market data retrieved successfully. The data structure may vary depending on the asset type and available information."
        }
    }

    private fun generateGeneralResponse(
        originalQuery: String,
        results: Map<String, Any>,
        analysis: QueryAnalysis
    ): String =
        buildString {
            appendLine("✅ **Request Processed**")
            appendLine()
            appendLine("I've processed your query: \"$originalQuery\"")
            appendLine()

            if (results.isNotEmpty()) {
                appendLine("**Data Retrieved:**")
                results.keys.forEach { key ->
                    appendLine("• ${key.replace("_", " ").replaceFirstChar { it.uppercase() }}")
                }
                appendLine()
            }

            appendLine("💡 **What would you like to do next?**")
            appendLine("• Ask for portfolio analysis")
            appendLine("• Get market data for specific assets")
            appendLine("• Check for corporate events")
            appendLine("• View FX rates")
        }

    private fun extractPortfolioCode(query: String): String? {
        // Extract portfolio code from various query patterns
        // Users refer to portfolios by their code (e.g., "USV") not UUID
        val patterns =
            listOf(
                Regex("portfolio[\\s-]*code[\\s-]*(\\w+)", RegexOption.IGNORE_CASE),
                Regex("portfolio[\\s-]*(\\w+)", RegexOption.IGNORE_CASE),
                Regex("analyze[\\s-]*portfolio[\\s-]*(\\w+)", RegexOption.IGNORE_CASE),
                Regex("portfolio[\\s-]*id[\\s-]*(\\w+)", RegexOption.IGNORE_CASE)
            )

        patterns.forEach { pattern ->
            val match = pattern.find(query)
            if (match != null) {
                return match.groupValues[1].uppercase()
            }
        }

        return null
    }

    private fun extractAssetCode(query: String): String? {
        // Extract asset code from market data queries
        // Users refer to assets by their code (e.g., "MSFT", "AAPL") not UUID
        val patterns =
            listOf(
                Regex("market\\s+data\\s+for\\s+(\\w+)", RegexOption.IGNORE_CASE),
                Regex("get\\s+market\\s+data\\s+for\\s+(\\w+)", RegexOption.IGNORE_CASE),
                Regex("data\\s+for\\s+(\\w+)", RegexOption.IGNORE_CASE),
                Regex("for\\s+(\\w+)", RegexOption.IGNORE_CASE),
                Regex("asset\\s+code\\s+(\\w+)", RegexOption.IGNORE_CASE),
                Regex("symbol\\s+(\\w+)", RegexOption.IGNORE_CASE)
            )

        patterns.forEach { pattern ->
            val match = pattern.find(query)
            if (match != null) {
                return match.groupValues[1].uppercase()
            }
        }

        return null
    }

    private fun determineIntent(query: String): String =
        when {
            query.contains(
                "portfolio"
            ) &&
                (query.contains("analysis") || query.contains("analyze")) -> "portfolio_analysis"
            query.contains("list") && query.contains("portfolio") -> "list_portfolios"
            query.contains("market") && query.contains("overview") -> "market_overview"
            query.contains("events") && query.contains("load") -> "load_events"
            query.contains("positions") -> "get_positions"
            query.contains("market") -> "market_data"
            query.contains("connectivity") ||
                (
                    query.contains("service") &&
                        (
                            query.contains("status") ||
                                query.contains(
                                    "health"
                                ) ||
                                query.contains("ping")
                        )
                ) -> "verify_connectivity"
            else -> "general_query"
        }
}