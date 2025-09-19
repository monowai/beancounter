package com.beancounter.agent

// Note: Event contracts are in svc-event module, using generic types for now
import com.beancounter.agent.client.DataMcpClient
import com.beancounter.agent.client.EventMcpClient
import com.beancounter.agent.client.PositionMcpClient
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Beancounter AI Agent
 *
 * This agent orchestrates communication with the three MCP servers (Data, Event, Position)
 * and provides a unified interface for AI/LLM integration. It handles:
 * - Portfolio analysis and management
 * - Market data retrieval and analysis
 * - Corporate event processing
 * - Position valuation and reporting
 * - Natural language query processing
 */
@Service
class BeancounterAgent(
    private val dataMcpClient: DataMcpClient,
    private val eventMcpClient: EventMcpClient,
    private val positionMcpClient: PositionMcpClient,
    private val llmService: LlmService,
    private val dateUtils: DateUtils,
    private val tokenContextService: TokenContextService,
    @Autowired(required = false) private val springAiService: SpringAiService?
) {
    private val log = LoggerFactory.getLogger(BeancounterAgent::class.java)

    /**
     * Process a natural language query and return structured results
     */
    fun processQuery(
        query: String,
        context: Map<String, Any> = emptyMap()
    ): AgentResponse {
        log.info("Processing query: {}", query)

        return tokenContextService.withCurrentToken {
            try {
                // Use LLM to analyze the query and determine required actions
                val analysis = llmService.analyzeQuery(query, context)

                // Execute the determined actions
                val results = executeActions(analysis.actions)

                // Generate a natural language response
                val response = llmService.generateResponse(query, results, analysis, springAiService)

                AgentResponse(
                    query = query,
                    response = response,
                    actions = analysis.actions,
                    results = results,
                    timestamp = LocalDate.now()
                )
            } catch (e: Exception) {
                log.error("Error processing query: {}", query, e)
                AgentResponse(
                    query = query,
                    response = "I encountered an error processing your request: ${e.message}",
                    actions = emptyList(),
                    results = emptyMap(),
                    timestamp = LocalDate.now(),
                    error = e.message
                )
            }
        }
    }

    /**
     * Get comprehensive portfolio analysis
     */
    fun analyzePortfolio(
        portfolioId: String,
        date: String = "today"
    ): PortfolioAnalysis {
        log.info("Analyzing portfolio: {} as of {}", portfolioId, date)

        return tokenContextService.withCurrentToken {
            val portfolio = getPortfolio(portfolioId)
            val positions = getPortfolioPositions(portfolio, date)
            val events = getPortfolioEvents(portfolioId, date)
            val metrics = getPortfolioMetrics(portfolio, date)

            PortfolioAnalysis(
                portfolio = portfolio,
                positions = positions,
                events = events,
                metrics = metrics,
                analysisDate = date
            )
        }
    }

    /**
     * Get market overview with key metrics
     */
    fun getMarketOverview(): MarketOverview {
        log.info("Getting market overview")

        return tokenContextService.withCurrentToken {
            val markets = getMarkets()
            val currencies = getCurrencies()
            val fxRates = getCurrentFxRates()

            MarketOverview(
                markets = markets,
                currencies = currencies,
                fxRates = fxRates,
                timestamp = LocalDate.now()
            )
        }
    }

    /**
     * Execute a list of actions against the MCP servers
     */
    private fun executeActions(actions: List<AgentAction>): Map<String, Any> {
        val results = mutableMapOf<String, Any>()

        actions.forEach { action ->
            try {
                val result =
                    when (action.type) {
                        ActionType.GET_PORTFOLIO -> {
                            val portfolioCode = action.parameters["portfolioCode"] as? String
                            val portfolioId =
                                if (portfolioCode != null) {
                                    resolvePortfolioCodeToId(portfolioCode)
                                        ?: throw IllegalArgumentException("Portfolio code '$portfolioCode' not found")
                                } else {
                                    action.parameters["portfolioId"] as String
                                }
                            getPortfolio(portfolioId)
                        }
                        ActionType.GET_PORTFOLIOS -> getPortfolios()
                        ActionType.GET_POSITIONS ->
                            getPortfolioPositions(
                                action.parameters["portfolio"] as Portfolio,
                                resolveDateString(action.parameters["date"] as String)
                            )
                        ActionType.GET_EVENTS -> {
                            val assetCode = action.parameters["assetCode"] as? String
                            val assetId =
                                if (assetCode != null) {
                                    resolveAssetCodeToId(assetCode)
                                } else {
                                    action.parameters["assetId"] as String
                                }
                            getAssetEvents(assetId)
                        }
                        ActionType.GET_MARKET_DATA -> {
                            val assetCode = action.parameters["assetCode"] as? String
                            val assetId =
                                if (assetCode != null) {
                                    resolveAssetCodeToId(assetCode)
                                } else {
                                    action.parameters["assetId"] as String
                                }
                            getMarketData(assetId, resolveDateString(action.parameters["date"] as String))
                        }
                        ActionType.GET_FX_RATES ->
                            getFxRates(
                                action.parameters["fromCurrency"] as String,
                                action.parameters["toCurrency"] as String,
                                (action.parameters["date"] as? String)?.let { resolveDateString(it) }
                            )
                        ActionType.LOAD_EVENTS -> {
                            val portfolioCode = action.parameters["portfolioCode"] as? String
                            val portfolioId =
                                if (portfolioCode != null) {
                                    resolvePortfolioCodeToId(portfolioCode)
                                        ?: throw IllegalArgumentException("Portfolio code '$portfolioCode' not found")
                                } else {
                                    action.parameters["portfolioId"] as String
                                }
                            loadEventsForPortfolio(
                                portfolioId,
                                resolveDateString(action.parameters["fromDate"] as String)
                            )
                        }
                        ActionType.BACKFILL_EVENTS -> {
                            val portfolioCode = action.parameters["portfolioCode"] as? String
                            val portfolioId =
                                if (portfolioCode != null) {
                                    resolvePortfolioCodeToId(portfolioCode)
                                        ?: throw IllegalArgumentException("Portfolio code '$portfolioCode' not found")
                                } else {
                                    action.parameters["portfolioId"] as String
                                }
                            backfillEvents(
                                portfolioId,
                                resolveDateString(action.parameters["fromDate"] as String),
                                (action.parameters["toDate"] as? String)?.let { resolveDateString(it) }
                            )
                        }
                        ActionType.GET_MARKETS -> getMarkets()
                        ActionType.GET_CURRENCIES -> getCurrencies()
                        ActionType.QUERY_POSITIONS -> queryPositions(action.parameters["query"] as TrustedTrnQuery)
                        ActionType.BUILD_POSITIONS ->
                            buildPositions(
                                action.parameters["portfolio"] as Portfolio,
                                resolveDateString(action.parameters["date"] as String)
                            )
                        ActionType.VALUE_POSITIONS -> valuePositions(action.parameters["positions"] as PositionResponse)
                        ActionType.GET_METRICS ->
                            getPortfolioMetrics(
                                action.parameters["portfolio"] as Portfolio,
                                resolveDateString(action.parameters["date"] as String)
                            )
                        ActionType.GET_BREAKDOWN ->
                            getPositionBreakdown(
                                action.parameters["portfolio"] as Portfolio,
                                resolveDateString(action.parameters["date"] as String)
                            )
                        ActionType.VERIFY_CONNECTIVITY -> verifyServiceConnectivity()
                        ActionType.GET_LARGEST_HOLDINGS ->
                            getLargestHoldings(
                                action.parameters["portfolioCode"] as String
                            )
                        ActionType.GET_POSITION_NEWS -> getPositionNews(action.parameters["portfolioCode"] as String)
                        ActionType.GET_TOP_MOVERS -> getTopMovers(action.parameters["portfolioCode"] as String)
                        ActionType.ANALYZE_PORTFOLIO_PERFORMANCE ->
                            analyzePortfolioPerformance(
                                action.parameters["portfolioCode"] as String
                            )
                        ActionType.GENERATE_LLM_ANALYSIS -> generateLlmAnalysis(action.parameters)
                        ActionType.GET_CORPORATE_ACTIONS ->
                            getCorporateActions(
                                action.parameters["portfolioCode"] as String
                            )
                        ActionType.GET_UPCOMING_EVENTS ->
                            getUpcomingEvents(
                                action.parameters["portfolioCode"] as String
                            )
                        ActionType.GET_MARKET_EVENTS -> getMarketEvents()
                    }
                results[action.id] = result
            } catch (e: Exception) {
                log.error("Error executing action {}: {}", action.id, e.message, e)
                results[action.id] = mapOf("error" to e.message)
            }
        }

        return results
    }

    // Data Service Methods
    private fun getPortfolio(portfolioId: String): Portfolio = dataMcpClient.getPortfolio(portfolioId)

    private fun getPortfolioByCode(code: String): Portfolio = dataMcpClient.getPortfolioByCode(code)

    private fun getPortfolios(): PortfoliosResponse = dataMcpClient.getPortfolios()

    private fun getAsset(assetId: String): Asset = dataMcpClient.getAsset(assetId)

    private fun findOrCreateAsset(
        market: String,
        code: String,
        category: String = "EQUITY"
    ): Asset = dataMcpClient.findOrCreateAsset(market, code, category)

    private fun getPortfolioPositions(
        portfolio: Portfolio,
        date: String
    ): PositionResponse = positionMcpClient.getPortfolioPositions(portfolio, date)

    private fun getPortfolioMetrics(
        portfolio: Portfolio,
        date: String
    ): Map<String, Any> = positionMcpClient.getPortfolioMetrics(portfolio, date)

    private fun getMarketData(
        assetId: String,
        date: String
    ): Map<String, Any> = dataMcpClient.getMarketData(assetId, date)

    private fun getFxRates(
        fromCurrency: String,
        toCurrency: String,
        date: String? = null
    ): FxResponse = dataMcpClient.getFxRates(fromCurrency, toCurrency, date)

    private fun resolvePortfolioCodeToId(portfolioCode: String): String? =
        try {
            val portfolios = getPortfolios()
            val portfolio = portfolios.data.find { it.code == portfolioCode }
            portfolio?.id
        } catch (e: Exception) {
            log.warn("Failed to resolve portfolio code '$portfolioCode' to ID", e)
            null
        }

    private fun resolveAssetCodeToId(assetCode: String): String {
        // For now, we'll use the asset code as the ID since we don't have an asset lookup service
        // In a real implementation, you'd call an asset service to resolve the code to UUID
        log.info("Using asset code '$assetCode' as asset ID (no asset resolution service available)")
        return assetCode
    }

    private fun resolveDateString(dateString: String): String =
        try {
            dateUtils.getFormattedDate(dateString).toString()
        } catch (e: Exception) {
            log.warn("Failed to resolve date string '$dateString', using as-is", e)
            dateString
        }

    private fun getCurrentFxRates(): Map<String, FxResponse> {
        getCurrencies()
        val majorPairs = listOf("USD", "EUR", "GBP", "AUD", "NZD", "SGD")
        val rates = mutableMapOf<String, FxResponse>()

        majorPairs.forEach { from ->
            majorPairs.filter { it != from }.forEach { to ->
                try {
                    rates["$from-$to"] = getFxRates(from, to)
                } catch (e: Exception) {
                    log.warn("Failed to get FX rate for $from-$to: ${e.message}")
                }
            }
        }

        return rates
    }

    private fun getMarkets(): MarketResponse = dataMcpClient.getMarkets()

    private fun getCurrencies(): List<Currency> = dataMcpClient.getCurrencies().toList()

    // Event Service Methods
    private fun getAssetEvents(assetId: String): Map<String, Any> = eventMcpClient.getAssetEvents(assetId)

    private fun getPortfolioEvents(
        portfolioId: String,
        date: String
    ): Map<String, Any> {
        // Get portfolio first to find assets
        val portfolio = getPortfolio(portfolioId)
        val positions = getPortfolioPositions(portfolio, date)

        // Get events for all assets in the portfolio
        val allEvents = mutableListOf<Map<String, Any>>()
        positions.data.positions.values.forEach { position ->
            try {
                val assetEvents = getAssetEvents(position.asset.id)
                val eventsData = assetEvents["data"] as? List<*> ?: emptyList<Any>()
                eventsData.forEach { event ->
                    if (event is Map<*, *>) {
                        allEvents.add(event as Map<String, Any>)
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to get events for asset ${position.asset.id}: ${e.message}")
            }
        }

        return mapOf("data" to allEvents)
    }

    fun loadEventsForPortfolio(
        portfolioId: String,
        fromDate: String
    ): Map<String, Any> = eventMcpClient.loadEventsForPortfolio(portfolioId, fromDate)

    fun backfillEvents(
        portfolioId: String,
        fromDate: String,
        toDate: String? = null
    ): Map<String, Any> = eventMcpClient.backfillEvents(portfolioId, fromDate, toDate)

    // Position Service Methods
    private fun queryPositions(query: TrustedTrnQuery): PositionResponse = positionMcpClient.queryPositions(query)

    private fun buildPositions(
        portfolio: Portfolio,
        date: String
    ): PositionResponse = positionMcpClient.buildPositions(portfolio, date)

    private fun valuePositions(positionResponse: PositionResponse): PositionResponse =
        positionMcpClient.valuePositions(positionResponse)

    private fun getPositionBreakdown(
        portfolio: Portfolio,
        date: String
    ): Map<String, Any> = positionMcpClient.getPositionBreakdown(portfolio, date)

    /**
     * Verify connectivity to all MCP services
     */
    private fun verifyServiceConnectivity(): ServiceHealthStatus {
        log.info("Verifying connectivity to all MCP services")

        // Use the existing HealthService to check all services
        val healthService = HealthService(dataMcpClient, eventMcpClient, positionMcpClient)
        return healthService.checkAllServicesHealth()
    }

    /**
     * Get largest holdings in a portfolio
     */
    private fun getLargestHoldings(portfolioCode: String): PositionResponse {
        log.info("Getting largest holdings for portfolio: {}", portfolioCode)

        val portfolioId = resolvePortfolioCodeToId(portfolioCode)
        if (portfolioId == null) {
            log.warn("Could not resolve portfolio code: {}", portfolioCode)
            return PositionResponse()
        }

        // Get all positions for the portfolio
        val portfolio =
            Portfolio(id = portfolioId)
        val positions = positionMcpClient.getPortfolioPositions(portfolio, resolveDateString("today"))

        // Sort by market value and return (the response generation will handle the top 5)
        return positions
    }

    /**
     * Get news and events for portfolio positions
     */
    private fun getPositionNews(portfolioCode: String): Map<String, Any> {
        log.info("Getting position news for portfolio: {}", portfolioCode)

        val portfolioId = resolvePortfolioCodeToId(portfolioCode)
        if (portfolioId == null) {
            log.warn("Could not resolve portfolio code: {}", portfolioCode)
            return mapOf("data" to emptyList<Any>())
        }

        // Get positions first
        val portfolio =
            Portfolio(id = portfolioId)
        val positions = positionMcpClient.getPortfolioPositions(portfolio, resolveDateString("today"))

        // Get events for each position
        val allEvents = mutableListOf<Map<String, Any>>()

        positions.data.positions.values.forEach { position ->
            try {
                val events = eventMcpClient.getAssetEvents(position.asset.id)
                if (events is Map<*, *>) {
                    val eventsList = events["data"] as? List<Map<String, Any>>
                    eventsList?.forEach { event ->
                        val eventMap = event as Map<String, Any?>
                        val enrichedEvent = mutableMapOf<String, Any>()
                        eventMap.forEach { (key, value) ->
                            if (value != null) {
                                enrichedEvent[key] = value
                            }
                        }
                        enrichedEvent["assetName"] = position.asset.name ?: "Unknown"
                        enrichedEvent["assetCode"] = position.asset.code
                        allEvents.add(enrichedEvent)
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to get events for asset ${position.asset.id}: ${e.message}")
            }
        }

        return mapOf("data" to allEvents)
    }

    /**
     * Get top movers in a portfolio
     */
    private fun getTopMovers(portfolioCode: String): PositionResponse {
        log.info("Getting top movers for portfolio: {}", portfolioCode)

        val portfolioId = resolvePortfolioCodeToId(portfolioCode)
        if (portfolioId == null) {
            log.warn("Could not resolve portfolio code: {}", portfolioCode)
            return PositionResponse()
        }

        // Get all positions for the portfolio
        val portfolio =
            Portfolio(id = portfolioId)
        val positions = positionMcpClient.getPortfolioPositions(portfolio, resolveDateString("today"))

        // Filter positions that have price change data and sort by change
        val positionsWithChange =
            positions.data.positions.values.filter {
                it.moneyValues[com.beancounter.common.model.Position.In.BASE]?.priceData?.changePercent != null
            }

        // Create new Positions object with filtered positions
        val filteredPositions =
            com.beancounter.common.model
                .Positions()
        positionsWithChange.forEach { position ->
            filteredPositions.add(position)
        }

        return PositionResponse(data = filteredPositions)
    }

    /**
     * Analyze portfolio performance
     */
    private fun analyzePortfolioPerformance(portfolioCode: String): PositionResponse {
        log.info("Analyzing portfolio performance for: {}", portfolioCode)

        val portfolioId = resolvePortfolioCodeToId(portfolioCode)
        if (portfolioId == null) {
            log.warn("Could not resolve portfolio code: {}", portfolioCode)
            return PositionResponse()
        }

        // Get all positions for the portfolio
        val portfolio =
            Portfolio(id = portfolioId)
        val positions = positionMcpClient.getPortfolioPositions(portfolio, resolveDateString("today"))

        // Return all positions for performance analysis
        return positions
    }

    /**
     * Generate LLM analysis using external API
     */
    private fun generateLlmAnalysis(parameters: Map<String, Any>): Map<String, Any> {
        log.info("Generating LLM analysis with parameters: {}", parameters.keys)

        val query = parameters["query"] as? String ?: "Portfolio analysis"
        val analysisType = parameters["analysisType"] as? String ?: "general"
        val portfolioData = parameters["portfolioData"] as? Map<String, Any> ?: emptyMap()

        val analysis =
            if (springAiService != null) {
                springAiService.generateAnalysis(query, portfolioData, analysisType)
            } else {
                log.warn("SpringAiService not available, returning fallback analysis")
                "## 🤖 AI Analysis\n\n> **Note:** Spring AI is not configured. Configure Ollama or OpenAI to enable enhanced analysis.\n\n**Basic Analysis:**\n- Review the portfolio data for insights\n- Consider consulting with a financial advisor for detailed analysis\n- Monitor market conditions and adjust strategy as needed"
            }

        return mapOf(
            "analysis" to analysis,
            "query" to query,
            "analysisType" to analysisType,
            "data" to portfolioData
        )
    }

    /**
     * Get corporate actions for portfolio positions
     */
    private fun getCorporateActions(portfolioCode: String): Map<String, Any> {
        log.info("Getting corporate actions for portfolio: {}", portfolioCode)

        val portfolioId = resolvePortfolioCodeToId(portfolioCode)
        if (portfolioId == null) {
            log.warn("Could not resolve portfolio code: {}", portfolioCode)
            return mapOf("data" to emptyList<Any>())
        }

        // Get positions first
        val portfolio =
            Portfolio(id = portfolioId)
        val positions = positionMcpClient.getPortfolioPositions(portfolio, resolveDateString("today"))

        // Get corporate actions for each position
        val allCorporateActions = mutableListOf<Map<String, Any>>()

        positions.data.positions.values.forEach { position ->
            try {
                val events = eventMcpClient.getAssetEvents(position.asset.id)
                if (events is Map<*, *>) {
                    val eventsList = events["data"] as? List<Map<String, Any>>
                    eventsList?.forEach { event ->
                        val eventMap = event as Map<String, Any?>
                        val eventType = eventMap["type"] as? String

                        // Filter for corporate actions (dividends, splits, mergers, etc.)
                        if (eventType?.lowercase() in
                            listOf("dividend", "split", "merger", "acquisition", "earnings", "spinoff", "rights")
                        ) {
                            val enrichedEvent = mutableMapOf<String, Any>()
                            eventMap.forEach { (key, value) ->
                                if (value != null) {
                                    enrichedEvent[key] = value
                                }
                            }
                            enrichedEvent["assetName"] = position.asset.name ?: "Unknown"
                            enrichedEvent["assetCode"] = position.asset.code
                            allCorporateActions.add(enrichedEvent)
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to get events for asset ${position.asset.id}: ${e.message}")
            }
        }

        // Sort by date (most recent first)
        val sortedActions =
            allCorporateActions.sortedByDescending {
                it["date"] as? String ?: "0000-00-00"
            }

        return mapOf("data" to sortedActions)
    }

    /**
     * Get upcoming events for portfolio and market
     */
    private fun getUpcomingEvents(portfolioCode: String): Map<String, Any> {
        log.info("Getting upcoming events for portfolio: {}", portfolioCode)

        val portfolioId = resolvePortfolioCodeToId(portfolioCode)
        if (portfolioId == null) {
            log.warn("Could not resolve portfolio code: {}", portfolioCode)
            return mapOf("portfolioEvents" to emptyList<Any>(), "marketEvents" to emptyList<Any>())
        }

        // Get portfolio events
        val portfolio =
            Portfolio(id = portfolioId)
        val positions = positionMcpClient.getPortfolioPositions(portfolio, resolveDateString("today"))

        val upcomingPortfolioEvents = mutableListOf<Map<String, Any>>()
        val today = LocalDate.now()
        val futureDate = today.plusDays(30) // Next 30 days

        positions.data.positions.values.forEach { position ->
            try {
                val events = eventMcpClient.getAssetEvents(position.asset.id)
                if (events is Map<*, *>) {
                    val eventsList = events["data"] as? List<Map<String, Any>>
                    eventsList?.forEach { event ->
                        val eventMap = event as Map<String, Any?>
                        val eventDate = eventMap["date"] as? String

                        // Check if event is in the future
                        if (eventDate != null) {
                            try {
                                val eventLocalDate = LocalDate.parse(eventDate)
                                if (eventLocalDate.isAfter(today) && eventLocalDate.isBefore(futureDate)) {
                                    val enrichedEvent = mutableMapOf<String, Any>()
                                    eventMap.forEach { (key, value) ->
                                        if (value != null) {
                                            enrichedEvent[key] = value
                                        }
                                    }
                                    enrichedEvent["assetName"] = position.asset.name ?: "Unknown"
                                    enrichedEvent["assetCode"] = position.asset.code
                                    upcomingPortfolioEvents.add(enrichedEvent)
                                }
                            } catch (e: Exception) {
                                log.warn("Failed to parse event date: {}", eventDate, e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to get events for asset ${position.asset.id}: ${e.message}")
            }
        }

        // Sort by date
        val sortedPortfolioEvents =
            upcomingPortfolioEvents.sortedBy {
                it["date"] as? String ?: "9999-12-31"
            }

        // For now, return empty market events (could be enhanced with market data service)
        val marketEvents = emptyList<Map<String, Any>>()

        return mapOf(
            "portfolioEvents" to sortedPortfolioEvents,
            "marketEvents" to marketEvents
        )
    }

    /**
     * Get market-wide events and announcements
     */
    private fun getMarketEvents(): Map<String, Any> {
        log.info("Getting market-wide events")

        // For now, return placeholder data
        // This could be enhanced to call a market data service or news API
        val marketEvents =
            listOf(
                mapOf(
                    "description" to "Federal Reserve Interest Rate Decision",
                    "date" to
                        LocalDate
                            .now()
                            .plusDays(7)
                            .toString(),
                    "category" to "Monetary Policy",
                    "impact" to "Market-wide",
                    "source" to "Federal Reserve"
                ),
                mapOf(
                    "description" to "Q4 Earnings Season Begins",
                    "date" to
                        LocalDate
                            .now()
                            .plusDays(14)
                            .toString(),
                    "category" to "Earnings",
                    "impact" to "Sector-specific",
                    "source" to "Market Calendar"
                ),
                mapOf(
                    "description" to "Monthly Jobs Report",
                    "date" to
                        LocalDate
                            .now()
                            .plusDays(3)
                            .toString(),
                    "category" to "Economic Data",
                    "impact" to "Market-wide",
                    "source" to "Bureau of Labor Statistics"
                )
            )

        return mapOf("data" to marketEvents)
    }

    fun getAiStatus(): Map<String, Any> =
        mapOf(
            "springAiServiceAvailable" to (springAiService != null),
            "springAiConfigured" to (springAiService?.isConfigured() ?: false),
            "ollamaUrl" to "http://localhost:11434",
            "model" to "llama3:8b"
        )
}