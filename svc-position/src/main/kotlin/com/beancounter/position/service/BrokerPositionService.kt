package com.beancounter.position.service

import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for building positions scoped by broker.
 * Used for broker reconciliation - comparing holdings with broker statements.
 *
 * This service:
 * - Fetches transactions for a specific broker across all portfolios
 * - Builds positions using the standard accumulation logic (including split adjustments)
 * - Filters out cash positions (accumulated from trade settlements but not relevant for reconciliation)
 */
@Service
class BrokerPositionService(
    private val trnService: TrnService,
    private val positionService: PositionService,
    private val positionValuationService: PositionValuationService
) {
    private val log = LoggerFactory.getLogger(BrokerPositionService::class.java)

    // Markets to exclude from broker reconciliation
    private val excludedMarkets = setOf("CASH", "PRIVATE")

    // Asset categories to exclude from broker reconciliation
    private val excludedCategories = setOf("CASH", "ACCOUNT", "TRADE", "BANK ACCOUNT")

    /**
     * Builds positions for all transactions from a specific broker.
     * Includes all transaction types with proper split adjustments.
     *
     * @param brokerId broker identifier
     * @param valuationDate date for position valuation
     * @param value whether to include market values
     * @return positions for all assets held with the broker (excluding cash)
     */
    fun getPositions(
        brokerId: String,
        valuationDate: String = DateUtils.TODAY,
        value: Boolean = true
    ): PositionResponse {
        val trnResponse = trnService.queryByBroker(brokerId, valuationDate)

        if (trnResponse.data.isEmpty()) {
            log.debug("No transactions found for broker: {}", brokerId)
            return PositionResponse()
        }

        // Sort transactions by trade date (required by Accumulator)
        val sortedTransactions = trnResponse.data.sortedBy { it.tradeDate }

        // Use the first transaction's portfolio as context for currency settings
        val contextPortfolio = sortedTransactions.first().portfolio

        // Build positions from broker transactions using the normal accumulation flow
        val positionRequest =
            PositionRequest(
                contextPortfolio.id,
                sortedTransactions
            )
        val positionResponse = positionService.build(contextPortfolio, positionRequest)
        val positions = positionResponse.data

        // Filter out cash positions - they are accumulated from trade settlements
        // but not relevant for broker reconciliation
        filterCashPositions(positions)

        if (!valuationDate.equals(DateUtils.TODAY, ignoreCase = true)) {
            positions.asAt = valuationDate
        }

        log.debug(
            "Built {} positions for broker: {}, asAt: {}",
            positions.positions.size,
            brokerId,
            valuationDate
        )

        // Value the positions if requested
        return if (value) {
            valuePositions(positions)
        } else {
            PositionResponse(positions)
        }
    }

    /**
     * Removes cash positions from the positions map.
     * Cash positions are created during trade settlement accumulation
     * but are not relevant for broker reconciliation.
     */
    private fun filterCashPositions(positions: com.beancounter.common.model.Positions) {
        positions.positions.entries.removeIf { (_, position) ->
            position.asset.market.code in excludedMarkets ||
                position.asset.category in excludedCategories
        }
    }

    /**
     * Values positions without sending market value updates.
     * Broker positions span multiple portfolios so MV updates don't apply.
     */
    private fun valuePositions(positions: com.beancounter.common.model.Positions): PositionResponse {
        if (!positions.hasPositions()) {
            return PositionResponse(positions)
        }

        val assets =
            positions.positions.values.map {
                com.beancounter.common.input.AssetInput(
                    it.asset.market.code,
                    it.asset.code
                )
            }

        return PositionResponse(
            positionValuationService.value(positions, assets)
        )
    }
}