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
            "currency" to portfolio.currency.code,
            "baseCurrency" to portfolio.base.code,
            "asAt" to valuationDate,
            "totalPositions" to positions.positions.size,
            "hasPositions" to positions.hasPositions(),
            "totals" to
                mapOf(
                    "base" to
                        positions.totals[com.beancounter.common.model.Position.In.BASE]?.let { totals ->
                            mapOf(
                                "marketValue" to totals.marketValue,
                                "purchases" to totals.purchases,
                                "sales" to totals.sales,
                                "gain" to totals.gain,
                                "income" to totals.income,
                                "irr" to totals.irr,
                                "currency" to totals.currency.code
                            )
                        },
                    "portfolio" to
                        positions.totals[com.beancounter.common.model.Position.In.PORTFOLIO]?.let { totals ->
                            mapOf(
                                "marketValue" to totals.marketValue,
                                "purchases" to totals.purchases,
                                "sales" to totals.sales,
                                "gain" to totals.gain,
                                "income" to totals.income,
                                "irr" to totals.irr,
                                "currency" to totals.currency.code
                            )
                        },
                    "trade" to
                        positions.totals[com.beancounter.common.model.Position.In.TRADE]?.let { totals ->
                            mapOf(
                                "marketValue" to totals.marketValue,
                                "purchases" to totals.purchases,
                                "sales" to totals.sales,
                                "gain" to totals.gain,
                                "income" to totals.income,
                                "irr" to totals.irr,
                                "currency" to totals.currency.code
                            )
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
                    "id" to portfolio.id,
                    "code" to portfolio.code,
                    "name" to portfolio.name,
                    "currency" to portfolio.currency.code,
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
                                "id" to position.asset.id,
                                "code" to position.asset.code,
                                "name" to position.asset.name,
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
                                    position
                                        .getMoneyValues(
                                            com.beancounter.common.model.Position.In.BASE,
                                            portfolio.base
                                        ).let { mv ->
                                            mapOf(
                                                "costValue" to mv.costValue,
                                                "marketValue" to mv.marketValue,
                                                "unrealisedGain" to mv.unrealisedGain,
                                                "realisedGain" to mv.realisedGain,
                                                "totalGain" to mv.totalGain,
                                                "dividends" to mv.dividends,
                                                "currency" to mv.currency.code
                                            )
                                        },
                                "portfolio" to
                                    position
                                        .getMoneyValues(
                                            com.beancounter.common.model.Position.In.PORTFOLIO,
                                            portfolio.currency
                                        ).let { mv ->
                                            mapOf(
                                                "costValue" to mv.costValue,
                                                "marketValue" to mv.marketValue,
                                                "unrealisedGain" to mv.unrealisedGain,
                                                "realisedGain" to mv.realisedGain,
                                                "totalGain" to mv.totalGain,
                                                "dividends" to mv.dividends,
                                                "currency" to mv.currency.code
                                            )
                                        }
                            )
                    )
                }
        )
    }

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