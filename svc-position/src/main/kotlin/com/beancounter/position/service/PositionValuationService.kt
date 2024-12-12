package com.beancounter.position.service

import IrrCalculator
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
import com.beancounter.common.utils.PercentUtils
import com.beancounter.position.model.ValuationData
import com.beancounter.position.utils.FxUtils
import com.beancounter.position.valuation.MarketValue
import com.beancounter.position.valuation.RoiCalculator
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
    private val irrCalculator: IrrCalculator
) {
    private val percentUtils = PercentUtils()
    private val roiCalculator = RoiCalculator()
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

        val tradeCurrency = getTradeCurrency(positions)
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
        calculateWeightsAndGains(positions)
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

    private fun getTradeCurrency(positions: Positions): Currency =
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

    private fun calculateWeightsAndGains(positions: Positions) {
        val baseTotals = positions.totals[Position.In.BASE]!!
        val refTotals = positions.totals[Position.In.PORTFOLIO]!!
        val tradeTotals = positions.totals[Position.In.TRADE]!!
        val theDate = dateUtils.getDate(positions.asAt)
        for (position in positions.positions.values) {
            val tradeMoneyValues =
                position.getMoneyValues(
                    Position.In.TRADE,
                    position.asset.market.currency
                )
            tradeMoneyValues.weight =
                percentUtils.percent(
                    tradeMoneyValues.marketValue,
                    refTotals.marketValue
                )

            val baseMoneyValues =
                position.getMoneyValues(
                    Position.In.BASE,
                    positions.portfolio.base
                )
            baseMoneyValues.weight =
                percentUtils.percent(
                    baseMoneyValues.marketValue,
                    baseTotals.marketValue
                )

            val portfolioMoneyValues =
                position.getMoneyValues(
                    Position.In.PORTFOLIO,
                    positions.portfolio.currency
                )
            portfolioMoneyValues.weight =
                percentUtils.percent(
                    tradeMoneyValues.marketValue,
                    tradeTotals.marketValue
                )

            val roi = roiCalculator.calculateROI(tradeMoneyValues)

            if (position.asset.market.code != "CASH") {
                position.periodicCashFlows.add(
                    position,
                    theDate
                )
                positions.periodicCashFlows.addAll(position.periodicCashFlows.cashFlows)
                val irr =
                    calculateIrrSafely(
                        position.periodicCashFlows,
                        "Failed to calculate IRR for ${position.asset.code}"
                    )
                setTotals(
                    tradeTotals,
                    tradeMoneyValues,
                    roi,
                    irr
                )
                setTotals(
                    baseTotals,
                    baseMoneyValues,
                    roi,
                    irr
                )
                setTotals(
                    refTotals,
                    portfolioMoneyValues,
                    roi,
                    irr
                )
            } else {
                tradeTotals.cash = tradeTotals.cash.add(tradeMoneyValues.marketValue)
                baseTotals.cash = baseTotals.cash.add(baseMoneyValues.marketValue)
                refTotals.cash = refTotals.cash.add(portfolioMoneyValues.marketValue)
            }
        }
    }

    private fun setTotals(
        totals: Totals,
        moneyValues: MoneyValues,
        roi: BigDecimal,
        irr: BigDecimal
    ) {
        moneyValues.roi = roi
        moneyValues.irr = irr
        totals.purchases = totals.purchases.add(moneyValues.purchases)
        totals.sales = totals.sales.add(moneyValues.sales)
        totals.income = totals.income.add(moneyValues.dividends)
        totals.gain = totals.gain.add(moneyValues.totalGain)
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
        message: String
    ): BigDecimal =
        try {
            BigDecimal(irrCalculator.calculate(periodicCashFlows))
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