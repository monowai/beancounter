package com.beancounter.position.service

import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Totals
import com.beancounter.common.utils.CashUtils
import com.beancounter.position.model.ValuationData
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.kotlin.SentryContext
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException

/**
 * Value the positions.
 */
@Service
class PositionValuationService(
    private val config: PositionValuationConfig,
    private val calculationSupport: PositionCalculationSupport,
    private val cashUtils: CashUtils = CashUtils()
) {
    private val logger = LoggerFactory.getLogger(PositionValuationService::class.java)

    fun value(
        positions: Positions,
        assets: Collection<AssetInput>
    ): Positions {
        if (assets.isEmpty()) {
            return positions // Nothing to value
        }
        logger.debug(
            "Requesting valuation positions: {}, code: {}, asAt: {}...",
            positions.positions.size,
            positions.portfolio.code,
            positions.asAt
        )

        val (priceResponse, fxResponse) =
            runBlocking {
                getValuationData(positions)
            }
        if (priceResponse.data.isEmpty()) {
            logger.info(
                "No prices found on date {}",
                positions.asAt
            )
            return positions // Prevent NPE
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

        calculateMarketValues(
            positions,
            priceResponse,
            fxResponse,
            tradeCurrency
        )
        weightsAndGains(positions)
        val irr =
            calculateIrrSafely(
                positions.periodicCashFlows,
                "Failed to calculate IRR for ${positions.portfolio.code}"
            )
        baseTotals.irr = irr
        pfTotals.irr = irr
        tradeTotals.irr = irr

        logger.debug(
            "Completed valuation of {} positions.",
            positions.positions.size
        )
        return positions
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

    private fun calculateMarketValues(
        positions: Positions,
        priceResponse: PriceResponse,
        fxResponse: FxResponse,
        tradeCurrency: Currency
    ) {
        val baseTotals = positions.totals[Position.In.BASE]!!
        val refTotals = positions.totals[Position.In.PORTFOLIO]!!
        val tradeTotals = positions.totals[Position.In.TRADE]!!
        for (marketData in priceResponse.data) {
            val position =
                config.marketValue.value(
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
                    position.asset.market.currency
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
        val baseTotals = positions.totals[Position.In.BASE]!!
        val refTotals = positions.totals[Position.In.PORTFOLIO]!!
        val tradeTotals = positions.totals[Position.In.TRADE]!!
        val asAtDate = config.dateUtils.getDate(positions.asAt)

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
        val tradeMoneyValues =
            calculationSupport.calculateTradeMoneyValues(
                positionContext.position,
                totalsGroup.tradeTotals
            )
        val baseMoneyValues =
            calculationSupport.calculateBaseMoneyValues(
                positionContext.position,
                totalsGroup.baseTotals,
                positionContext.positions.portfolio.base
            )
        val portfolioMoneyValues =
            calculationSupport.calculatePortfolioMoneyValues(
                positionContext.position,
                tradeMoneyValues,
                totalsGroup.tradeTotals,
                positionContext.positions.portfolio.currency
            )

        val moneyValuesGroup = MoneyValuesGroup(tradeMoneyValues, baseMoneyValues, portfolioMoneyValues)

        if (!cashUtils.isCash(positionContext.position.asset)) {
            processNonCashPosition(positionContext, totalsGroup, moneyValuesGroup)
        } else {
            processCashPosition(totalsGroup, moneyValuesGroup)
        }
    }

    private fun processNonCashPosition(
        positionContext: PositionContext,
        totalsGroup: TotalsGroup,
        moneyValuesGroup: MoneyValuesGroup
    ) {
        val roi = calculationSupport.calculateRoi(moneyValuesGroup.tradeMoneyValues)
        positionContext.position.periodicCashFlows.add(positionContext.position, positionContext.asAtDate)
        positionContext.positions.periodicCashFlows.addAll(positionContext.position.periodicCashFlows.cashFlows)

        val irr = calculatePositionIrr(positionContext, roi)

        calculationSupport.updateTotals(totalsGroup.tradeTotals, moneyValuesGroup.tradeMoneyValues, roi, irr)
        calculationSupport.updateTotals(totalsGroup.baseTotals, moneyValuesGroup.baseMoneyValues, roi, irr)
        calculationSupport.updateTotals(totalsGroup.refTotals, moneyValuesGroup.portfolioMoneyValues, roi, irr)
    }

    /**
     * Calculate IRR for a position, using ROI for short holding periods.
     *
     * Industry best practice is to use simple ROI for short-term investments (typically < 1 year)
     * because XIRR can produce extreme/misleading annualized values for short periods.
     *
     * We use `opened` date to determine if the position was held long enough for XIRR.
     * The `opened` date tracks the current holding period - it's preserved after sell-out
     * for display purposes and reset when re-entering a position after complete sell-out.
     *
     * The threshold is configurable via `beancounter.irr` (default: 365 days).
     */
    private fun calculatePositionIrr(
        positionContext: PositionContext,
        roi: BigDecimal
    ): BigDecimal {
        val position = positionContext.position

        // Use opened date for holding period calculation - it tracks the current/last holding period
        // and is preserved after sell-out, reset on reentry
        val referenceDate = position.dateValues.opened

        val holdingDays =
            if (referenceDate != null) {
                java.time.temporal.ChronoUnit.DAYS
                    .between(referenceDate, positionContext.asAtDate)
            } else {
                0L
            }

        return if (holdingDays < config.minHoldingDaysForIrr) {
            roi
        } else {
            // Fallback to ROI if no cash flows available (shouldn't happen normally)
            if (position.periodicCashFlows.cashFlows.isEmpty()) {
                roi
            } else {
                calculateIrrSafely(
                    position.periodicCashFlows,
                    "Failed to calculate IRR for ${position.asset.code}"
                )
            }
        }
    }

    private fun processCashPosition(
        totalsGroup: TotalsGroup,
        moneyValuesGroup: MoneyValuesGroup
    ) {
        calculationSupport.updateCashTotals(totalsGroup, moneyValuesGroup)
    }

    suspend fun getValuationData(
        positions: Positions,
        token: String = config.tokenService.bearerToken
    ): ValuationData =
        withContext(SentryContext()) {
            try {
                val fxRequest =
                    config.fxUtils.buildRequest(
                        positions.portfolio.base,
                        positions
                    )
                val priceRequest =
                    PriceRequest.of(
                        positions.asAt,
                        positions
                    )
                val priceDeferred =
                    async {
                        config.priceService.getPrices(
                            priceRequest,
                            token
                        )
                    }
                val fxDeferred =
                    async {
                        config.fxRateService.getRates(
                            fxRequest,
                            token
                        )
                    }

                val prices = priceDeferred.await()
                val rates = fxDeferred.await()

                ValuationData(
                    prices,
                    rates
                )
            } catch (e: CancellationException) {
                throw IllegalStateException(
                    "Thread was interrupted while fetching market data.",
                    e
                )
            }
        }

    private fun calculateIrrSafely(
        periodicCashFlows: PeriodicCashFlows,
        message: String
    ): BigDecimal {
        val result = config.irrCalculator.calculate(periodicCashFlows)

        if (result == 0.0 && periodicCashFlows.cashFlows.isNotEmpty()) {
            logger.warn("IRR calculation returned zero for non-empty cash flows [$message]")
            val breadcrumb =
                Breadcrumb(message).apply {
                    category = "data"
                    level = SentryLevel.WARNING
                }
            Sentry.addBreadcrumb(breadcrumb)
        }

        return BigDecimal(result)
    }
}