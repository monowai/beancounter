package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Currency
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Totals
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.irr.IrrCalculator
import com.beancounter.position.model.ValuationData
import com.beancounter.position.utils.FxUtils
import com.beancounter.position.valuation.MarketValue
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.kotlin.SentryContext
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.math3.exception.NoBracketingException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * Value the positions.
 */
@Service
class PositionValuationService(
    private val marketValue: MarketValue,
    private val fxUtils: FxUtils,
    private val priceService: PriceService,
    private val fxRateService: FxService,
    private val tokenService: TokenService,
    private val dateUtils: DateUtils,
    private val irrCalculator: IrrCalculator,
    private val calculationSupport: PositionCalculationSupport
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
                calculationSupport.calculatePortfolioRoi(pfTotals),
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
            positions.positions.values
                .firstOrNull()
                ?.asset
                ?.market
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
        val asAtDate = dateUtils.getDate(positions.asAt)

        for (position in positions.positions.values) {
            processPositionWeightsAndGains(position, positions, baseTotals, refTotals, tradeTotals, asAtDate)
        }
    }

    private fun processPositionWeightsAndGains(
        position: Position,
        positions: Positions,
        baseTotals: Totals,
        refTotals: Totals,
        tradeTotals: Totals,
        asAtDate: LocalDate
    ) {
        val tradeMoneyValues = calculationSupport.calculateTradeMoneyValues(position, refTotals)
        val baseMoneyValues =
            calculationSupport.calculateBaseMoneyValues(position, baseTotals, positions.portfolio.base)
        val portfolioMoneyValues =
            calculationSupport.calculatePortfolioMoneyValues(
                position,
                tradeMoneyValues,
                tradeTotals,
                positions.portfolio.currency
            )

        if (position.asset.market.code != "CASH") {
            processNonCashPosition(
                position,
                positions,
                tradeTotals,
                baseTotals,
                refTotals,
                tradeMoneyValues,
                baseMoneyValues,
                portfolioMoneyValues,
                asAtDate
            )
        } else {
            processCashPosition(
                tradeTotals,
                baseTotals,
                refTotals,
                tradeMoneyValues,
                baseMoneyValues,
                portfolioMoneyValues
            )
        }
    }

    private fun processNonCashPosition(
        position: Position,
        positions: Positions,
        tradeTotals: Totals,
        baseTotals: Totals,
        refTotals: Totals,
        tradeMoneyValues: MoneyValues,
        baseMoneyValues: MoneyValues,
        portfolioMoneyValues: MoneyValues,
        asAtDate: LocalDate
    ) {
        val roi = calculationSupport.calculateRoi(tradeMoneyValues)
        position.periodicCashFlows.add(position, asAtDate)
        positions.periodicCashFlows.addAll(position.periodicCashFlows.cashFlows)

        val irr =
            calculateIrrSafely(
                position.periodicCashFlows,
                roi,
                "Failed to calculate IRR for ${position.asset.code}"
            )

        calculationSupport.updateTotals(tradeTotals, tradeMoneyValues, roi, irr)
        calculationSupport.updateTotals(baseTotals, baseMoneyValues, roi, irr)
        calculationSupport.updateTotals(refTotals, portfolioMoneyValues, roi, irr)
    }

    private fun processCashPosition(
        tradeTotals: Totals,
        baseTotals: Totals,
        refTotals: Totals,
        tradeMoneyValues: MoneyValues,
        baseMoneyValues: MoneyValues,
        portfolioMoneyValues: MoneyValues
    ) {
        calculationSupport.updateCashTotals(
            tradeTotals,
            baseTotals,
            refTotals,
            tradeMoneyValues,
            baseMoneyValues,
            portfolioMoneyValues
        )
    }

    suspend fun getValuationData(
        positions: Positions,
        token: String = tokenService.bearerToken
    ): ValuationData =
        withContext(SentryContext()) {
            try {
                val fxRequest =
                    fxUtils.buildRequest(
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
                        priceService.getPrices(
                            priceRequest,
                            token
                        )
                    }
                val fxDeferred =
                    async {
                        fxRateService.getRates(
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
        roi: BigDecimal,
        message: String
    ): BigDecimal =
        try {
            BigDecimal(irrCalculator.calculate(periodicCashFlows, roi = roi))
        } catch (e: NoBracketingException) {
            logger.error(
                "Failed to calculate IRR [$message]",
                e
            )
            val breadcrumb =
                Breadcrumb(message).apply {
                    category = "data"
                    level = SentryLevel.ERROR
                }
            Sentry.addBreadcrumb(breadcrumb)

            BigDecimal.ZERO
        }
}