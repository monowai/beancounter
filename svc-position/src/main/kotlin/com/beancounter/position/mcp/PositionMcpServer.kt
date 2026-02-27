package com.beancounter.position.mcp

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.position.valuation.ValuationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * MCP Server for Beancounter Position Service
 *
 * Exposes portfolio position and valuation functionality through the Model Context Protocol
 * for AI integration. Uses actual business services rather than controllers.
 */
@Service
class PositionMcpServer(
    private val valuationService: ValuationService
) {
    private val log = LoggerFactory.getLogger(PositionMcpServer::class.java)

    companion object {
        const val KEY_MARKET_VALUE = "marketValue"
        const val KEY_CURRENCY = "currency"
        const val KEY_CODE = "code"
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_PURCHASES = "purchases"
        const val KEY_SALES = "sales"
        const val KEY_GAIN = "gain"
        const val KEY_INCOME = "income"
        const val KEY_IRR = "irr"
    }

    /**
     * Get portfolio positions by portfolio object with valuation
     */
    fun getPositions(
        portfolio: Portfolio,
        valuationDate: String = "today",
        includeValues: Boolean = true
    ): PositionResponse {
        log.info("MCP: Getting positions for portfolio: {} as at: {}", portfolio.code, valuationDate)
        return valuationService.getPositions(portfolio, valuationDate, includeValues)
    }

    /**
     * Build positions from a custom query
     */
    fun queryPositions(query: TrustedTrnQuery): PositionResponse {
        log.info("MCP: Querying positions with custom parameters for portfolio: {}", query.portfolio)
        return valuationService.build(query)
    }

    /**
     * Build positions for a portfolio and date
     */
    fun buildPositions(
        portfolio: Portfolio,
        valuationDate: String
    ): PositionResponse {
        log.info("MCP: Building positions for portfolio: {} as at: {}", portfolio.code, valuationDate)
        return valuationService.build(portfolio, valuationDate)
    }

    /**
     * Value existing positions (add market values)
     */
    fun valuePositions(positionResponse: PositionResponse): PositionResponse {
        log.info("MCP: Valuing positions for portfolio: {}", positionResponse.data.portfolio.code)
        return valuationService.value(positionResponse.data)
    }

    /**
     * Get portfolio performance metrics
     */
    fun getPortfolioMetrics(
        portfolio: Portfolio,
        valuationDate: String = "today"
    ): Map<String, Any> {
        log.info("MCP: Getting portfolio metrics for: {} as at: {}", portfolio.code, valuationDate)
        val positionResponse = valuationService.getPositions(portfolio, valuationDate, true)
        val positions = positionResponse.data

        return mapOf(
            "portfolioId" to portfolio.id,
            "portfolioCode" to portfolio.code,
            "portfolioName" to portfolio.name,
            KEY_CURRENCY to portfolio.currency.code,
            "baseCurrency" to portfolio.base.code,
            "asAt" to valuationDate,
            "totalPositions" to positions.positions.size,
            "hasPositions" to positions.hasPositions(),
            "totals" to
                mapOf(
                    "base" to
                        positions.totals[com.beancounter.common.model.Position.In.BASE]?.let { totals ->
                            totalsToMap(totals)
                        },
                    "portfolio" to
                        positions.totals[com.beancounter.common.model.Position.In.PORTFOLIO]?.let { totals ->
                            totalsToMap(totals)
                        },
                    "trade" to
                        positions.totals[com.beancounter.common.model.Position.In.TRADE]?.let { totals ->
                            totalsToMap(totals)
                        }
                )
        )
    }

    /**
     * Get detailed position breakdown for a portfolio
     */
    fun getPositionBreakdown(
        portfolio: Portfolio,
        valuationDate: String = "today"
    ): Map<String, Any> {
        log.info("MCP: Getting position breakdown for: {} as at: {}", portfolio.code, valuationDate)
        val positionResponse = valuationService.getPositions(portfolio, valuationDate, true)
        val positions = positionResponse.data

        return mapOf(
            "portfolio" to
                mapOf(
                    KEY_ID to portfolio.id,
                    KEY_CODE to portfolio.code,
                    KEY_NAME to portfolio.name,
                    KEY_CURRENCY to portfolio.currency.code,
                    "baseCurrency" to portfolio.base.code
                ),
            "asAt" to valuationDate,
            "summary" to
                mapOf(
                    "totalPositions" to positions.positions.size,
                    "hasPositions" to positions.hasPositions(),
                    "mixedCurrencies" to positions.isMixedCurrencies
                ),
            "positions" to
                positions.positions.values.map { position ->
                    mapOf(
                        "asset" to
                            mapOf(
                                KEY_ID to position.asset.id,
                                KEY_CODE to position.asset.code,
                                KEY_NAME to position.asset.name,
                                "market" to position.asset.market.code,
                                "category" to position.asset.category
                            ),
                        "quantityValues" to
                            mapOf(
                                "total" to position.quantityValues.getTotal(),
                                "sold" to position.quantityValues.sold,
                                "purchased" to position.quantityValues.purchased
                            ),
                        "moneyValues" to
                            mapOf(
                                "base" to
                                    moneyValuesToMap(
                                        position.getMoneyValues(
                                            com.beancounter.common.model.Position.In.BASE,
                                            portfolio.base
                                        )
                                    ),
                                "portfolio" to
                                    moneyValuesToMap(
                                        position.getMoneyValues(
                                            com.beancounter.common.model.Position.In.PORTFOLIO,
                                            portfolio.currency
                                        )
                                    )
                            )
                    )
                }
        )
    }

    private fun totalsToMap(totals: com.beancounter.common.model.Totals): Map<String, Any?> =
        mapOf(
            KEY_MARKET_VALUE to totals.marketValue,
            KEY_PURCHASES to totals.purchases,
            KEY_SALES to totals.sales,
            KEY_GAIN to totals.gain,
            KEY_INCOME to totals.income,
            KEY_IRR to totals.irr,
            KEY_CURRENCY to totals.currency.code
        )

    private fun moneyValuesToMap(mv: com.beancounter.common.model.MoneyValues): Map<String, Any?> =
        mapOf(
            "costValue" to mv.costValue,
            KEY_MARKET_VALUE to mv.marketValue,
            "unrealisedGain" to mv.unrealisedGain,
            "realisedGain" to mv.realisedGain,
            "totalGain" to mv.totalGain,
            "dividends" to mv.dividends,
            KEY_CURRENCY to mv.currency.code
        )

    /**
     * Get all available MCP tools/functions exposed by this service
     */
    fun getAvailableTools(): Map<String, String> =
        mapOf(
            "get_positions" to "Get portfolio positions with valuation",
            "query_positions" to "Build positions from a custom query",
            "build_positions" to "Build positions for a portfolio and date",
            "value_positions" to "Value existing positions (add market values)",
            "get_portfolio_metrics" to "Get portfolio performance metrics and totals",
            "get_position_breakdown" to "Get detailed position breakdown for a portfolio"
        )
}