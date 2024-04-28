package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.exception.SystemException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Totals
import com.beancounter.common.utils.PercentUtils
import com.beancounter.position.model.ValuationData
import com.beancounter.position.utils.FxUtils
import com.beancounter.position.valuation.MarketValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Obtains necessary prices and fx rates for the requested positions, returning them as valued positions in
 * various currencies.
 */
@Service
class PositionValuationService internal constructor(
    private val marketValue: MarketValue,
    private val fxUtils: FxUtils,
    private val priceService: PriceService,
    private val fxRateService: FxService,
    private val tokenService: TokenService,
) {
    val percentUtils = PercentUtils()

    fun value(
        positions: Positions,
        assets: Collection<AssetInput>,
    ): Positions {
        if (assets.isEmpty()) {
            return positions // Nothing to value
        }
        log.debug(
            "Requesting valuation positions: {}, code: {}, asAt: {}...",
            positions.positions.size,
            positions.portfolio.code,
            positions.asAt,
        )

        // Set market data into the positions
        // There's an issue here that without a price, gains are not computed
        val (priceResponse, fxResponse) = getValuationData(positions)
        if (priceResponse.data.isEmpty()) {
            log.info("No prices found on date {}", positions.asAt)
            return positions // Prevent NPE
        }
        val baseTotals = Totals()
        val refTotals = Totals()
        for (marketData in priceResponse.data) {
            val position = marketValue.value(positions, marketData, fxResponse.data.rates)
            val baseMoneyValues = position.getMoneyValues(Position.In.BASE, positions.portfolio.base)
            val refMoneyValues = position.getMoneyValues(Position.In.PORTFOLIO, position.asset.market.currency)

            // Directly add to the totals, reducing method calls
            baseTotals.total = baseTotals.total.add(baseMoneyValues.marketValue)
            refTotals.total = refTotals.total.add(refMoneyValues.marketValue)
        }
        // Set the accumulated totals once after all computations are done
        positions.setTotal(Position.In.BASE, baseTotals)
        positions.setTotal(Position.In.PORTFOLIO, refTotals)

        // Iterate through each position only once and update weights accordingly
        for (position in positions.positions.values) {
            // Calculate and set the weight for base currency
            val baseMoneyValues = position.getMoneyValues(Position.In.BASE, positions.portfolio.base)
            baseMoneyValues.weight = percentUtils.percent(baseMoneyValues.marketValue, baseTotals.total)

            // Calculate and set the weight for portfolio currency
            val portfolioMoneyValues = position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency)
            portfolioMoneyValues.weight = percentUtils.percent(portfolioMoneyValues.marketValue, refTotals.total)

            // Calculate and set the weight for trade currency
            val tradeMoneyValues = position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)
            tradeMoneyValues.weight = percentUtils.percent(tradeMoneyValues.marketValue, refTotals.total)
        }

        log.debug(
            "Completed valuation of {} positions.",
            positions.positions.size,
        )
        return positions
    }

    private fun getValuationData(positions: Positions): ValuationData {
        try {
            val fxRequest = fxUtils.buildRequest(positions.portfolio.base, positions)
            val priceRequest = PriceRequest.of(positions.asAt, positions)
            val token = tokenService.bearerToken
            // Start both asynchronous operations simultaneously
            val futurePriceResponse =
                CompletableFuture.supplyAsync {
                    priceService.getPrices(priceRequest, token)
                }

            val futureFxResponse =
                CompletableFuture.supplyAsync {
                    fxRateService.getRates(fxRequest, token)
                }

            // Wait for both futures to complete and then create ValuationData
            val rates = futureFxResponse.get(30, TimeUnit.SECONDS)
            val prices = futurePriceResponse.get(180, TimeUnit.SECONDS)

            return ValuationData(prices, rates)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt() // set the interrupt flag
            throw IllegalStateException("Thread was interrupted while fetching market data.", e)
        } catch (e: ExecutionException) {
            throw SystemException("Error fetching market data")
        } catch (e: TimeoutException) {
            throw SystemException("Timed out waiting for market data")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PositionValuationService::class.java)
    }
}
