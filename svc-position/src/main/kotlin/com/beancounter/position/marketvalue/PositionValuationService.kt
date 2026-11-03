package com.beancounter.position.marketvalue

import com.beancounter.common.contracts.BulkClassificationResponse
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Totals
import com.beancounter.common.telemetry.runBlockingTraced
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.irr.PositionIrr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

private fun Positions.totalsFor(key: Position.In): Totals = requireNotNull(totals[key]) { "Missing totals for $key" }

/**
 * Value the positions.
 */
@Service
class PositionValuationService(
    private val marketValue: MarketValue,
    private val gains: Gains,
    private val dateUtils: DateUtils,
    private val positionIrr: PositionIrr,
    private val valuationDataSource: ValuationDataSource,
    private val calculationSupport: PositionCalculationSupport
) {
    private val cashUtils = CashUtils()
    private val logger = LoggerFactory.getLogger(PositionValuationService::class.java)

    /** [live] values at live (delayed) quotes while each market is in session. */
    fun value(
        positions: Positions,
        assets: Collection<AssetInput>,
        live: Boolean = false
    ): Positions {
        if (assets.isEmpty()) {
            return positions // Nothing to value
        }
        logger.trace(
            "Requesting valuation positions: {}, code: {}, asAt: {}...",
            positions.positions.size,
            positions.portfolio.code,
            positions.asAt
        )

        val (priceResponse, fxResponse, classifications) =
            runBlockingTraced {
                valuationDataSource.read(positions, live = live)
            }

        val tradeCurrency = tradeCurrency(positions)
        val baseTotals = Totals(positions.portfolio.base)
        val pfTotals = Totals(positions.portfolio.currency)
        val tradeTotals = Totals(tradeCurrency)

        positions.setTotal(
            Position.In.BASE,
            baseTotals
        )
        positions.setTotal(
            Position.In.PORTFOLIO,
            pfTotals
        )
        positions.setTotal(
            Position.In.TRADE,
            tradeTotals
        )

        // Calculate market values only if we have prices
        // For sold-out positions (zero quantity), prices are filtered out but we still need
        // to calculate gains from realized gains and historical cash flows
        if (priceResponse.data.isNotEmpty()) {
            calculateMarketValues(
                positions,
                priceResponse,
                fxResponse,
                tradeCurrency
            )
        } else {
            logger.debug(
                "No prices found on date {} - calculating gains from realized values only",
                positions.asAt
            )
        }

        // Ensure gains are calculated for ALL positions, including those without market data
        // (e.g., closed positions where quantity = 0)
        calculateGainsForAllPositions(positions)

        weightsAndGains(positions)
        val irr =
            positionIrr.of(
                positions.periodicCashFlows,
                "Failed to calculate IRR for ${positions.portfolio.code}"
            )
        baseTotals.irr = irr
        pfTotals.irr = irr
        tradeTotals.irr = irr
        classify(positions, classifications)

        logger.trace(
            "Completed valuation of {} positions.",
            positions.positions.size
        )
        return positions
    }

    /**
     * Applies sector/industry from svc-data. Cash assets are always the "Cash" sector.
     * A failed classification read arrives empty, so positions just lack the data.
     */
    private fun classify(
        positions: Positions,
        classifications: BulkClassificationResponse
    ) {
        positions.positions.values.forEach { position ->
            if (position.asset.effectiveReportCategory == AssetCategory.REPORT_CASH) {
                position.asset.sector = AssetCategory.REPORT_CASH
            } else {
                classifications.data[position.asset.id]?.let { classification ->
                    position.asset.sector = classification.sector
                    position.asset.industry = classification.industry
                }
            }
        }
    }

    private fun tradeCurrency(positions: Positions): Currency =
        if (positions.isMixedCurrencies) {
            positions.portfolio.base
        } else {
            // Use the position's actual trade currency, not market.currency
            // This handles PRIVATE market assets (bank accounts) that have their own currency
            positions.positions.values
                .firstOrNull()
                ?.moneyValues
                ?.get(Position.In.TRADE)
                ?.currency ?: positions.portfolio.base
        }

    /**
     * Calculates gains (unrealisedGain and totalGain) for all positions.
     *
     * This ensures that positions without market data (e.g., closed positions with quantity=0)
     * have their totalGain correctly calculated as: unrealisedGain + dividends + realisedGain.
     *
     * For positions that already went through MarketValue.value() -> Gains.value(),
     * this will recalculate with the same formula, producing the same result.
     */
    private fun calculateGainsForAllPositions(positions: Positions) {
        for (position in positions.positions.values) {
            if (!cashUtils.isCash(position.asset)) {
                val quantity = position.quantityValues.getTotal()
                for (moneyValues in position.moneyValues.values) {
                    gains.value(quantity, moneyValues)
                }
            }
        }
    }

    private fun calculateMarketValues(
        positions: Positions,
        priceResponse: PriceResponse,
        fxResponse: FxResponse,
        tradeCurrency: Currency
    ) {
        val baseTotals = positions.totalsFor(Position.In.BASE)
        val refTotals = positions.totalsFor(Position.In.PORTFOLIO)
        val tradeTotals = positions.totalsFor(Position.In.TRADE)
        for (marketData in priceResponse.data) {
            val position =
                marketValue.value(
                    positions,
                    marketData,
                    fxResponse.data.rates
                )

            val baseMoneyValues =
                position.getMoneyValues(
                    Position.In.BASE,
                    positions.portfolio.base
                )
            val refMoneyValues =
                position.getMoneyValues(
                    Position.In.PORTFOLIO,
                    // PORTFOLIO bucket denominates value in the portfolio's
                    // reporting currency (mirrors MarketValue.value()). Using
                    // asset.market.currency leaked the PRIVATE market's
                    // placeholder USD into composite POLICY positions whose
                    // accountingType is SGD, double-counting the FX rate.
                    positions.portfolio.currency
                )
            val tradeMoneyValues =
                position.getMoneyValues(
                    Position.In.TRADE,
                    tradeCurrency
                )

            baseTotals.marketValue = baseTotals.marketValue.add(baseMoneyValues.marketValue)
            refTotals.marketValue = refTotals.marketValue.add(refMoneyValues.marketValue)
            tradeTotals.marketValue = tradeTotals.marketValue.add(tradeMoneyValues.marketValue)
        }
    }

    private fun weightsAndGains(positions: Positions) {
        val baseTotals = positions.totalsFor(Position.In.BASE)
        val refTotals = positions.totalsFor(Position.In.PORTFOLIO)
        val tradeTotals = positions.totalsFor(Position.In.TRADE)
        val asAtDate = dateUtils.getDate(positions.asAt)

        val totalsGroup = TotalsGroup(tradeTotals, baseTotals, refTotals)
        for (position in positions.positions.values) {
            val positionContext = PositionContext(position, positions, asAtDate)
            processPositionWeightsAndGains(positionContext, totalsGroup)
        }
    }

    private fun processPositionWeightsAndGains(
        positionContext: PositionContext,
        totalsGroup: TotalsGroup
    ) {
        val tradeMoneyValues = calculationSupport.calculateTradeMoneyValues(positionContext.position)
        val baseMoneyValues =
            calculationSupport.calculateBaseMoneyValues(
                positionContext.position,
                positionContext.positions.portfolio.base
            )
        val portfolioMoneyValues =
            calculationSupport.calculatePortfolioMoneyValues(
                positionContext.position,
                positionContext.positions.portfolio.currency
            )

        val moneyValuesGroup = MoneyValuesGroup(tradeMoneyValues, baseMoneyValues, portfolioMoneyValues)
        calculationSupport.assignWeights(moneyValuesGroup, totalsGroup.baseTotals)

        if (!cashUtils.isCash(positionContext.position.asset)) {
            processNonCashPosition(positionContext, totalsGroup, moneyValuesGroup)
        } else {
            calculationSupport.updateCashTotals(totalsGroup, moneyValuesGroup)
        }
    }

    private fun processNonCashPosition(
        positionContext: PositionContext,
        totalsGroup: TotalsGroup,
        moneyValuesGroup: MoneyValuesGroup
    ) {
        val roi = calculationSupport.calculateRoi(moneyValuesGroup.tradeMoneyValues)
        positionIrr.anchorOpeningCostBasis(positionContext.position, moneyValuesGroup.tradeMoneyValues)
        positionContext.position.periodicCashFlows.add(positionContext.position, positionContext.asAtDate)
        positionContext.positions.periodicCashFlows.addAll(positionContext.position.periodicCashFlows.cashFlows)

        val irr = positionIrr.ofPosition(positionContext.position, positionContext.asAtDate, roi)

        calculationSupport.updateTotals(totalsGroup.tradeTotals, moneyValuesGroup.tradeMoneyValues, roi, irr)
        calculationSupport.updateTotals(totalsGroup.baseTotals, moneyValuesGroup.baseMoneyValues, roi, irr)
        calculationSupport.updateTotals(totalsGroup.refTotals, moneyValuesGroup.portfolioMoneyValues, roi, irr)
    }
}