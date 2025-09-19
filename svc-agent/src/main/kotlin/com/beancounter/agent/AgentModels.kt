package com.beancounter.agent

import com.beancounter.common.contracts.FxResponse
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
    VERIFY_CONNECTIVITY,

    // Portfolio analysis actions
    GET_LARGEST_HOLDINGS,
    GET_POSITION_NEWS,
    ANALYZE_PORTFOLIO_PERFORMANCE,
    GET_TOP_MOVERS,
    GENERATE_LLM_ANALYSIS,

    // Corporate actions analysis
    GET_CORPORATE_ACTIONS,
    GET_MARKET_EVENTS,
    GET_UPCOMING_EVENTS
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
    val fxRates: Map<String, FxResponse>,
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
        analysis: QueryAnalysis,
        springAiService: SpringAiService?
    ): String
}

/**
 * Simple LLM Service implementation for basic query processing
 */
class SimpleLlmService : LlmService {
    private val log = LoggerFactory.getLogger(SimpleLlmService::class.java)

    override fun analyzeQuery(
        query: String,
        context: Map<String, Any>
    ): QueryAnalysis {
        val lowerQuery = query.lowercase()

        val actions = mutableListOf<AgentAction>()
        val entities = mutableMapOf<String, String>()

        // Simple keyword-based analysis
        when {
            lowerQuery.contains("corporate") && lowerQuery.contains("action") -> {
                val portfolioCode = extractPortfolioCode(query) ?: "default"
                entities["portfolioCode"] = portfolioCode
                actions.add(
                    AgentAction(
                        id = "get_corporate_actions",
                        type = ActionType.GET_CORPORATE_ACTIONS,
                        parameters = mapOf("portfolioCode" to portfolioCode),
                        description = "Get corporate actions for portfolio positions"
                    )
                )
            }

            lowerQuery.contains("upcoming") && (lowerQuery.contains("event") || lowerQuery.contains("action")) -> {
                val portfolioCode = extractPortfolioCode(query) ?: "default"
                entities["portfolioCode"] = portfolioCode
                actions.add(
                    AgentAction(
                        id = "get_upcoming_events",
                        type = ActionType.GET_UPCOMING_EVENTS,
                        parameters = mapOf("portfolioCode" to portfolioCode),
                        description = "Get upcoming events for portfolio and market"
                    )
                )
            }

            lowerQuery.contains("market") && lowerQuery.contains("event") -> {
                actions.add(
                    AgentAction(
                        id = "get_market_events",
                        type = ActionType.GET_MARKET_EVENTS,
                        parameters = emptyMap(),
                        description = "Get market-wide events and announcements"
                    )
                )
            }

            lowerQuery.contains("largest") && (lowerQuery.contains("holding") || lowerQuery.contains("position")) -> {
                val portfolioCode = extractPortfolioCode(query) ?: "default"
                entities["portfolioCode"] = portfolioCode
                actions.add(
                    AgentAction(
                        id = "get_largest_holdings",
                        type = ActionType.GET_LARGEST_HOLDINGS,
                        parameters = mapOf("portfolioCode" to portfolioCode),
                        description = "Get largest holdings in portfolio"
                    )
                )
            }

            lowerQuery.contains(
                "news"
            ) &&
                (
                    lowerQuery.contains(
                        "holding"
                    ) ||
                        lowerQuery.contains("position") ||
                        lowerQuery.contains("portfolio")
                ) -> {
                val portfolioCode = extractPortfolioCode(query) ?: "default"
                entities["portfolioCode"] = portfolioCode
                actions.add(
                    AgentAction(
                        id = "get_position_news",
                        type = ActionType.GET_POSITION_NEWS,
                        parameters = mapOf("portfolioCode" to portfolioCode),
                        description = "Get news for portfolio positions"
                    )
                )
            }

            lowerQuery.contains("latest") && lowerQuery.contains("news") -> {
                val portfolioCode = extractPortfolioCode(query) ?: "default"
                entities["portfolioCode"] = portfolioCode
                actions.add(
                    AgentAction(
                        id = "get_position_news",
                        type = ActionType.GET_POSITION_NEWS,
                        parameters = mapOf("portfolioCode" to portfolioCode),
                        description = "Get latest news for portfolio positions"
                    )
                )
            }

            lowerQuery.contains("recent") && lowerQuery.contains("news") -> {
                val portfolioCode = extractPortfolioCode(query) ?: "default"
                entities["portfolioCode"] = portfolioCode
                actions.add(
                    AgentAction(
                        id = "get_position_news",
                        type = ActionType.GET_POSITION_NEWS,
                        parameters = mapOf("portfolioCode" to portfolioCode),
                        description = "Get recent news for portfolio positions"
                    )
                )
            }

            lowerQuery.contains("top") && (lowerQuery.contains("mover") || lowerQuery.contains("performer")) -> {
                val portfolioCode = extractPortfolioCode(query) ?: "default"
                entities["portfolioCode"] = portfolioCode
                actions.add(
                    AgentAction(
                        id = "get_top_movers",
                        type = ActionType.GET_TOP_MOVERS,
                        parameters = mapOf("portfolioCode" to portfolioCode),
                        description = "Get top movers in portfolio"
                    )
                )
            }

            lowerQuery.contains("performance") && lowerQuery.contains("portfolio") -> {
                val portfolioCode = extractPortfolioCode(query) ?: "default"
                entities["portfolioCode"] = portfolioCode
                actions.add(
                    AgentAction(
                        id = "analyze_portfolio_performance",
                        type = ActionType.ANALYZE_PORTFOLIO_PERFORMANCE,
                        parameters = mapOf("portfolioCode" to portfolioCode),
                        description = "Analyze portfolio performance"
                    )
                )
            }

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

            (lowerQuery.contains("fx") || lowerQuery.contains("exchange") || lowerQuery.contains("currency")) &&
                (lowerQuery.contains("rate") || lowerQuery.contains("rates")) -> {
                val currencies = extractCurrencies(query)
                actions.add(
                    AgentAction(
                        id = "get_fx_rates",
                        type = ActionType.GET_FX_RATES,
                        parameters =
                            mapOf(
                                "fromCurrency" to (currencies?.first ?: "USD"),
                                "toCurrency" to (currencies?.second ?: "EUR"),
                                "date" to "today"
                            ),
                        description =
                            "Get FX rates between ${currencies?.first ?: "USD"} " +
                                "and ${currencies?.second ?: "EUR"}"
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
        analysis: QueryAnalysis,
        springAiService: SpringAiService?
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
            "fx_rates" -> {
                generateFxRatesResponse(results, originalQuery, springAiService)
            }
            "get_largest_holdings" -> {
                generateLargestHoldingsResponse(results, originalQuery, springAiService)
            }
            "get_position_news" -> {
                generatePositionNewsResponse(results, originalQuery, springAiService)
            }
            "get_top_movers" -> {
                generateTopMoversResponse(results, originalQuery, springAiService)
            }
            "analyze_portfolio_performance" -> {
                generatePortfolioPerformanceResponse(results, originalQuery, springAiService)
            }
            "get_corporate_actions" -> {
                generateCorporateActionsResponse(results, originalQuery, springAiService)
            }
            "get_upcoming_events" -> {
                generateUpcomingEventsResponse(results, originalQuery, springAiService)
            }
            "get_market_events" -> {
                generateMarketEventsResponse(results, originalQuery, springAiService)
            }
            else -> {
                generateGeneralResponse(originalQuery, results)
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
                    val marketValue = portfolio.marketValue.let { "$${String.format(Locale.US, "%.2f", it)}" }
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

    private fun generateFxRatesResponse(
        results: Map<String, Any>,
        originalQuery: String,
        springAiService: SpringAiService?
    ): String {
        val fxResponse = results["get_fx_rates"] as? FxResponse

        // Use SpringAI for enhanced analysis if available
        if (springAiService != null && fxResponse != null && fxResponse.data.rates.isNotEmpty()) {
            try {
                val portfolioData =
                    mapOf(
                        "fxRates" to fxResponse.data.rates,
                        "query" to originalQuery,
                        "timestamp" to (
                            fxResponse.data.rates.values
                                .firstOrNull()
                                ?.date ?: "N/A"
                        )
                    )

                return springAiService.generateAnalysis(
                    originalQuery,
                    portfolioData,
                    "fx_rates_analysis"
                )
            } catch (e: Exception) {
                log.warn("SpringAI analysis failed for FX rates, falling back to template response: ${e.message}")
            }
        }

        return if (fxResponse != null && fxResponse.data.rates.isNotEmpty()) {
            buildString {
                appendLine("💱 **Foreign Exchange Rates**")
                appendLine()

                fxResponse.data.rates.forEach { (_, fxRate) ->
                    appendLine("**Currency Pair:** ${fxRate.from.code} → ${fxRate.to.code}")
                    appendLine("**Exchange Rate:** ${fxRate.rate}")
                    appendLine("**Date:** ${fxRate.date}")
                    appendLine()

                    appendLine("💡 **Quick Calculations:**")
                    val rate = fxRate.rate.toDouble()
                    appendLine("• 1 ${fxRate.from.code} = ${fxRate.rate} ${fxRate.to.code}")
                    appendLine("• 100 ${fxRate.from.code} = ${String.format("%.2f", rate * 100)} ${fxRate.to.code}")
                    appendLine("• 1000 ${fxRate.from.code} = ${String.format("%.2f", rate * 1000)} ${fxRate.to.code}")
                    appendLine()
                }
            }
        } else {
            "FX rates retrieved successfully. The exchange rate data is now available for your analysis."
        }
    }

    private fun generateGeneralResponse(
        originalQuery: String,
        results: Map<String, Any>
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

    private fun extractCurrencies(query: String): Pair<String, String>? {
        // Extract currency codes from FX rates queries
        // Common patterns: "USD to EUR", "USD/EUR", "USD-EUR", "USD EUR"
        val patterns =
            listOf(
                Regex("(\\w{3})\\s+to\\s+(\\w{3})", RegexOption.IGNORE_CASE),
                Regex("(\\w{3})/(\\w{3})", RegexOption.IGNORE_CASE),
                Regex("(\\w{3})-(\\w{3})", RegexOption.IGNORE_CASE),
                Regex("(\\w{3})\\s+(\\w{3})", RegexOption.IGNORE_CASE),
                Regex("from\\s+(\\w{3})\\s+to\\s+(\\w{3})", RegexOption.IGNORE_CASE),
                Regex("convert\\s+(\\w{3})\\s+to\\s+(\\w{3})", RegexOption.IGNORE_CASE)
            )

        patterns.forEach { pattern ->
            val match = pattern.find(query)
            if (match != null) {
                return match.groupValues[1].uppercase() to match.groupValues[2].uppercase()
            }
        }

        return null
    }

    private fun generateLargestHoldingsResponse(
        results: Map<String, Any>,
        originalQuery: String,
        springAiService: SpringAiService?
    ): String {
        val positionsResponse = results["get_largest_holdings"] as? PositionResponse

        return buildString {
            appendLine("🏆 **Your Largest Holdings**")
            appendLine()

            if (positionsResponse?.data?.hasPositions() == true) {
                // Sort positions by market value (descending) and take top 5
                val positions =
                    positionsResponse.data.positions.values
                        .toList()
                val topHoldings =
                    positions
                        .sortedByDescending {
                            it.moneyValues[com.beancounter.common.model.Position.In.BASE]?.marketValue?.toDouble()
                                ?: 0.0
                        }.take(5)

                appendLine("| Asset | Code | Market Value | % of Portfolio |")
                appendLine("|---|---|---|---|")

                val totalValue =
                    positions.sumOf {
                        it.moneyValues[com.beancounter.common.model.Position.In.BASE]?.marketValue?.toDouble() ?: 0.0
                    }

                topHoldings.forEach { position ->
                    val marketValue =
                        position.moneyValues[com.beancounter.common.model.Position.In.BASE]?.marketValue?.toDouble()
                            ?: 0.0
                    val percentage =
                        if (totalValue > 0) {
                            (marketValue / totalValue * 100)
                        } else {
                            0.0
                        }

                    appendLine(
                        "| ${position.asset.name} | ${position.asset.code} | " +
                            "$${String.format(Locale.US, "%.2f", marketValue)} | " +
                            "${String.format(Locale.US, "%.1f", percentage)}% |"
                    )
                }

                appendLine()
                appendLine(
                    "💡 **Analysis Ready:** This data can be used to generate contextual prompts for " +
                        "news analysis, performance tracking, and risk assessment."
                )
            } else {
                appendLine("No position data available.")
            }

            // Add Spring AI analysis
            appendLine()
            springAiService?.let { aiService ->
                val analysisData =
                    mapOf(
                        "positions" to (
                            positionsResponse
                                ?.data
                                ?.positions
                                ?.values
                                ?.toList() ?: emptyList()
                        ),
                        "total_value" to (
                            positionsResponse?.data?.positions?.values?.sumOf {
                                (it.moneyValues[com.beancounter.common.model.Position.In.BASE]?.marketValue ?: 0.0)
                                    .toDouble()
                            } ?: 0.0
                        )
                    )
                appendLine(aiService.generateAnalysis(originalQuery, analysisData, "get_largest_holdings"))
            }
        }
    }

    private fun generatePositionNewsResponse(
        results: Map<String, Any>,
        originalQuery: String,
        springAiService: SpringAiService?
    ): String {
        val eventsData = results["get_position_news"] as? Map<String, Any>

        return buildString {
            appendLine("📰 **News & Events for Your Positions**")
            appendLine()

            if (eventsData?.isNotEmpty() == true) {
                val events = eventsData["data"] as? List<Map<String, Any>>

                if (events?.isNotEmpty() == true) {
                    appendLine("| Asset | Event Type | Date | Description |")
                    appendLine("|---|---|---|---|")

                    events.take(10).forEach { event ->
                        appendLine(
                            "| ${event["assetName"] ?: "N/A"} | ${event["type"] ?: "N/A"} | " +
                                "${event["date"] ?: "N/A"} | ${event["description"] ?: "N/A"} |"
                        )
                    }

                    appendLine()
                    appendLine(
                        "💡 **Analysis Ready:** This event data can be used to generate contextual " +
                            "prompts for news analysis and market impact assessment."
                    )
                } else {
                    appendLine("No recent events found for your positions.")
                }
            } else {
                appendLine("No event data available.")
            }

            // Add Spring AI analysis
            appendLine()
            springAiService?.let { aiService ->
                appendLine(aiService.generateAnalysis(originalQuery, eventsData ?: emptyMap(), "get_position_news"))
            }
        }
    }

    private fun generateTopMoversResponse(
        results: Map<String, Any>,
        originalQuery: String,
        springAiService: SpringAiService?
    ): String {
        val positionsResponse = results["get_top_movers"] as? PositionResponse

        return buildString {
            appendLine("📈 **Top Movers in Your Portfolio**")
            appendLine()

            if (positionsResponse?.data?.hasPositions() == true) {
                // Sort by percentage change (descending)
                val positions =
                    positionsResponse.data.positions.values
                        .toList()
                val topMovers =
                    positions
                        .filter {
                            it.moneyValues[com.beancounter.common.model.Position.In.BASE]?.priceData?.changePercent !=
                                null
                        }.sortedByDescending {
                            it.moneyValues[com.beancounter.common.model.Position.In.BASE]
                                ?.priceData
                                ?.changePercent
                                ?.toDouble()
                                ?: 0.0
                        }.take(5)

                appendLine("| Asset | Code | Price Change | Market Value |")
                appendLine("|---|---|---|---|")

                topMovers.forEach { position ->
                    val change =
                        position.moneyValues[com.beancounter.common.model.Position.In.BASE]
                            ?.priceData
                            ?.changePercent
                            ?.toDouble()
                            ?: 0.0
                    val changeStr =
                        if (change >= 0) {
                            "+${String.format(Locale.US, "%.2f", change)}%"
                        } else {
                            "${String.format(Locale.US, "%.2f", change)}%"
                        }
                    val marketValue =
                        position.moneyValues[com.beancounter.common.model.Position.In.BASE]?.marketValue?.toDouble()
                            ?: 0.0

                    appendLine(
                        "| ${position.asset.name} | ${position.asset.code} | $changeStr | " +
                            "$${String.format(Locale.US, "%.2f", marketValue)} |"
                    )
                }

                appendLine()
                appendLine(
                    "💡 **Analysis Ready:** This performance data can be used to generate contextual " +
                        "prompts for trend analysis and market commentary."
                )
            } else {
                appendLine("No position data available for analysis.")
            }

            // Add Spring AI analysis
            appendLine()
            springAiService?.let { aiService ->
                val analysisData =
                    mapOf(
                        "positions" to (
                            positionsResponse
                                ?.data
                                ?.positions
                                ?.values
                                ?.toList() ?: emptyList()
                        ),
                        "top_movers" to (
                            positionsResponse
                                ?.data
                                ?.positions
                                ?.values
                                ?.toList()
                                ?.filter {
                                    it.moneyValues[com.beancounter.common.model.Position.In.BASE]
                                        ?.priceData
                                        ?.changePercent != null
                                }?.sortedByDescending {
                                    it.moneyValues[com.beancounter.common.model.Position.In.BASE]
                                        ?.priceData
                                        ?.changePercent
                                        ?.toDouble()
                                        ?: 0.0
                                }?.take(5) ?: emptyList()
                        )
                    )
                appendLine(aiService.generateAnalysis(originalQuery, analysisData, "get_top_movers"))
            }
        }
    }

    private fun generatePortfolioPerformanceResponse(
        results: Map<String, Any>,
        originalQuery: String,
        springAiService: SpringAiService?
    ): String {
        val positionsResponse =
            results["analyze_portfolio_performance"] as?
                PositionResponse

        return buildString {
            appendLine("📊 **Portfolio Performance Analysis**")
            appendLine()

            if (positionsResponse?.data?.hasPositions() == true) {
                val positions =
                    positionsResponse.data.positions.values
                        .toList()
                val totalValue =
                    positions.sumOf {
                        it.moneyValues[com.beancounter.common.model.Position.In.BASE]?.marketValue?.toDouble()
                            ?: 0.0
                    }
                val totalCost =
                    positions.sumOf {
                        it.moneyValues[com.beancounter.common.model.Position.In.BASE]?.costValue?.toDouble()
                            ?: 0.0
                    }
                val totalGainLoss = totalValue - totalCost
                val totalReturn = if (totalCost > 0) (totalGainLoss / totalCost * 100) else 0.0

                appendLine("**Portfolio Summary:**")
                appendLine("- Total Market Value: $${String.format(Locale.US, "%.2f", totalValue)}")
                appendLine("- Total Cost Basis: $${String.format(Locale.US, "%.2f", totalCost)}")
                appendLine("- Total Gain/Loss: $${String.format(Locale.US, "%.2f", totalGainLoss)}")
                appendLine("- Total Return: ${String.format(Locale.US, "%.2f", totalReturn)}%")
                appendLine()

                // Performance by position
                appendLine("**Performance by Position:**")
                appendLine("| Asset | Code | Gain/Loss | Return % |")
                appendLine("|---|---|---|---|")

                positions.forEach { position ->
                    val marketValue =
                        position.moneyValues[com.beancounter.common.model.Position.In.BASE]?.marketValue?.toDouble()
                            ?: 0.0
                    val costValue =
                        position.moneyValues[com.beancounter.common.model.Position.In.BASE]?.costValue?.toDouble()
                            ?: 0.0
                    val gainLoss = marketValue - costValue
                    val returnPct =
                        if (costValue > 0) {
                            (gainLoss / costValue * 100)
                        } else {
                            0.0
                        }

                    appendLine(
                        "| ${position.asset.name} | ${position.asset.code} | " +
                            "$${String.format(Locale.US, "%.2f", gainLoss)} | " +
                            "${String.format(Locale.US, "%.2f", returnPct)}% |"
                    )
                }

                appendLine()
                appendLine(
                    "💡 **Analysis Ready:** This performance data can be used to generate contextual " +
                        "prompts for portfolio optimization, risk analysis, and investment strategy recommendations."
                )
            } else {
                appendLine("No position data available for performance analysis.")
            }

            // Add Spring AI analysis
            appendLine()
            springAiService?.let { aiService ->
                val analysisData =
                    mapOf(
                        "positions" to (
                            positionsResponse
                                ?.data
                                ?.positions
                                ?.values
                                ?.toList() ?: emptyList()
                        ),
                        "total_value" to (
                            positionsResponse?.data?.positions?.values?.sumOf {
                                (it.moneyValues[com.beancounter.common.model.Position.In.BASE]?.marketValue ?: 0.0)
                                    .toDouble()
                            } ?: 0.0
                        ),
                        "total_cost" to (
                            positionsResponse?.data?.positions?.values?.sumOf {
                                (it.moneyValues[com.beancounter.common.model.Position.In.BASE]?.costValue ?: 0.0)
                                    .toDouble()
                            } ?: 0.0
                        )
                    )
                appendLine(aiService.generateAnalysis(originalQuery, analysisData, "analyze_portfolio_performance"))
            }
        }
    }

    private fun generateCorporateActionsResponse(
        results: Map<String, Any>,
        originalQuery: String,
        springAiService: SpringAiService?
    ): String {
        val eventsData = results["get_corporate_actions"] as? Map<String, Any>

        return buildString {
            appendLine("🏢 **Corporate Actions in Your Portfolio**")
            appendLine()

            if (eventsData?.isNotEmpty() == true) {
                val events = eventsData["data"] as? List<Map<String, Any>>

                if (events?.isNotEmpty() == true) {
                    // Group events by type
                    val eventsByType = events.groupBy { it["type"] as? String ?: "Unknown" }

                    eventsByType.forEach { (type, typeEvents) ->
                        appendLine("### $type")
                        appendLine()
                        appendLine("| Asset | Date | Description | Impact |")
                        appendLine("|---|---|---|---|")

                        typeEvents.take(10).forEach { event ->
                            val impact =
                                when (type.lowercase()) {
                                    "dividend" -> "💰 Income"
                                    "split" -> "📊 Share Adjustment"
                                    "merger" -> "🔄 Corporate Change"
                                    "acquisition" -> "🏗️ Corporate Change"
                                    "earnings" -> "📈 Financial Update"
                                    else -> "📋 Corporate Action"
                                }

                            appendLine(
                                "| ${event["assetName"] ?: "N/A"} | ${event["date"] ?: "N/A"} | " +
                                    "${event["description"] ?: "N/A"} | $impact |"
                            )
                        }
                        appendLine()
                    }

                    appendLine(
                        "💡 **Analysis Ready:** This corporate actions data can be used to generate " +
                            "contextual prompts for impact analysis, timing considerations, and portfolio adjustments."
                    )
                } else {
                    appendLine("No recent corporate actions found for your portfolio positions.")
                }
            } else {
                appendLine("No corporate actions data available.")
            }

            // Add Spring AI analysis
            appendLine()
            springAiService?.let { aiService ->
                appendLine(aiService.generateAnalysis(originalQuery, eventsData ?: emptyMap(), "get_corporate_actions"))
            }
        }
    }

    private fun generateUpcomingEventsResponse(
        results: Map<String, Any>,
        originalQuery: String,
        springAiService: SpringAiService?
    ): String {
        val eventsData = results["get_upcoming_events"] as? Map<String, Any>

        return buildString {
            appendLine("📅 **Upcoming Events & Actions**")
            appendLine()

            if (eventsData?.isNotEmpty() == true) {
                val portfolioEvents = eventsData["portfolioEvents"] as? List<Map<String, Any>>
                val marketEvents = eventsData["marketEvents"] as? List<Map<String, Any>>

                if (portfolioEvents?.isNotEmpty() == true) {
                    appendLine("### 🎯 **Your Portfolio Events**")
                    appendLine()
                    appendLine("| Asset | Event | Date | Type |")
                    appendLine("|---|---|---|---|")

                    portfolioEvents.take(10).forEach { event ->
                        val eventType = event["type"] as? String ?: "Event"
                        val eventIcon =
                            when (eventType.lowercase()) {
                                "dividend" -> "💰"
                                "earnings" -> "📊"
                                "split" -> "📈"
                                "merger" -> "🔄"
                                else -> "📋"
                            }

                        appendLine(
                            "| ${event["assetName"] ?: "N/A"} | $eventIcon ${event["description"] ?: "N/A"} | " +
                                "${event["date"] ?: "N/A"} | $eventType |"
                        )
                    }
                    appendLine()
                }

                if (marketEvents?.isNotEmpty() == true) {
                    appendLine("### 🌍 **Market-Wide Events**")
                    appendLine()
                    appendLine("| Event | Date | Impact |")
                    appendLine("|---|---|---|")

                    marketEvents.take(10).forEach { event ->
                        val impact = event["impact"] as? String ?: "Market-wide"
                        appendLine(
                            "| ${event["description"] ?: "N/A"} | ${event["date"] ?: "N/A"} | $impact |"
                        )
                    }
                    appendLine()
                }

                if (portfolioEvents.isNullOrEmpty() && marketEvents.isNullOrEmpty()) {
                    appendLine("No upcoming events found.")
                }

                appendLine(
                    "💡 **Analysis Ready:** This upcoming events data can be used to generate " +
                        "contextual prompts for event planning, impact assessment, and strategic positioning."
                )
            } else {
                appendLine("No upcoming events data available.")
            }

            // Add Spring AI analysis
            appendLine()
            springAiService?.let { aiService ->
                appendLine(aiService.generateAnalysis(originalQuery, eventsData ?: emptyMap(), "get_upcoming_events"))
            }
        }
    }

    private fun generateMarketEventsResponse(
        results: Map<String, Any>,
        originalQuery: String,
        springAiService: SpringAiService?
    ): String {
        val eventsData = results["get_market_events"] as? Map<String, Any>

        return buildString {
            appendLine("🌍 **Market-Wide Events & Announcements**")
            appendLine()

            if (eventsData?.isNotEmpty() == true) {
                val events = eventsData["data"] as? List<Map<String, Any>>

                if (events?.isNotEmpty() == true) {
                    // Group by category
                    val eventsByCategory =
                        events.groupBy {
                            it["category"] as? String ?: "General"
                        }

                    eventsByCategory.forEach { (category, categoryEvents) ->
                        appendLine("### $category")
                        appendLine()
                        appendLine("| Event | Date | Impact | Source |")
                        appendLine("|---|---|---|---|")

                        categoryEvents.take(10).forEach { event ->
                            val impact = event["impact"] as? String ?: "Market-wide"
                            val source = event["source"] as? String ?: "Market Data"

                            appendLine(
                                "| ${event["description"] ?: "N/A"} | ${event["date"] ?: "N/A"} | " +
                                    "$impact | $source |"
                            )
                        }
                        appendLine()
                    }

                    appendLine(
                        "💡 **Analysis Ready:** This market events data can be used to generate contextual " +
                            "prompts for market analysis, sector trends, and investment opportunities."
                    )
                } else {
                    appendLine("No market events found.")
                }
            } else {
                appendLine("No market events data available.")
            }

            // Add Spring AI analysis
            appendLine()
            springAiService?.let { aiService ->
                appendLine(aiService.generateAnalysis(originalQuery, eventsData ?: emptyMap(), "get_market_events"))
            }
        }
    }

    private fun determineIntent(query: String): String =
        when {
            query.contains("corporate") && query.contains("action") -> "get_corporate_actions"
            query.contains("upcoming") && (query.contains("event") || query.contains("action")) -> "get_upcoming_events"
            query.contains("market") && query.contains("event") -> "get_market_events"
            query.contains("latest") && query.contains("news") -> "get_position_news"
            query.contains("recent") && query.contains("news") -> "get_position_news"
            query.contains("largest") &&
                (
                    query.contains(
                        "holding"
                    ) ||
                        query.contains("position") ||
                        query.contains("portfolio")
                ) -> "get_largest_holdings"
            query.contains("news") &&
                (
                    query.contains(
                        "holding"
                    ) ||
                        query.contains("position") ||
                        query.contains("portfolio")
                ) -> "get_position_news"
            query.contains("top") &&
                (
                    query.contains(
                        "mover"
                    ) ||
                        query.contains("performer") ||
                        query.contains("portfolio")
                ) -> "get_top_movers"
            query.contains("performance") && query.contains("portfolio") -> "analyze_portfolio_performance"
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
            (query.contains("fx") || query.contains("exchange") || query.contains("currency")) &&
                (query.contains("rate") || query.contains("rates")) -> "fx_rates"
            else -> "general_query"
        }
}