package com.beancounter.position.service

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Totals
import com.beancounter.common.utils.MathUtils.Companion.percent
import com.beancounter.position.model.ValuationData
import com.beancounter.position.utils.FxUtils
import com.beancounter.position.valuation.MarketValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class PositionValuationService internal constructor(
    private val asyncMdService: AsyncMdService,
    private val marketValue: MarketValue,
    private val fxUtils: FxUtils
) {
    fun value(positions: Positions, assets: Collection<AssetInput>): Positions {
        if (assets.isEmpty()) {
            return positions // Nothing to value
        }
        log.debug(
            "Requested valuation of {} positions for {}...",
            positions.positions.size,
            positions.portfolio.code
        )

        // Set market data into the positions
        // There's an issue here that without a price, gains are not computed
        val (priceResponse, fxResponse1) = getValuationData(positions, assets)
        if (priceResponse == null) {
            log.info("No prices found on date {}", positions.asAt)
            return positions // Prevent NPE
        }
        val (data) = fxResponse1 ?: throw BusinessException("Unable to obtain FX Rates ")
        val rates = data.rates
        val baseTotals = Totals()
        val refTotals = Totals()
        for (marketData in priceResponse.data) {
            val position = marketValue.value(positions, marketData, rates)
            val baseAmount = position.getMoneyValues(
                Position.In.BASE, positions.portfolio.base
            ).marketValue
            baseTotals.total = baseTotals.total.add(baseAmount)
            val refAmount = position.getMoneyValues(
                Position.In.PORTFOLIO, position.asset.market.currency
            ).marketValue
            refTotals.total = refTotals.total.add(refAmount)
        }
        positions.setTotal(Position.In.BASE, baseTotals)
        for (position in positions.positions.values) {
            var moneyValues = position.getMoneyValues(
                Position.In.BASE,
                positions.portfolio.base
            )
            moneyValues.weight = percent(moneyValues.marketValue, baseTotals.total)
            moneyValues = position.getMoneyValues(
                Position.In.PORTFOLIO,
                positions.portfolio.currency
            )
            moneyValues.weight = percent(moneyValues.marketValue, refTotals.total)
            moneyValues = position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)
            moneyValues.weight = percent(moneyValues.marketValue, refTotals.total)
        }
        log.debug(
            "Completed valuation of {} positions for {}...",
            positions.positions.size,
            positions.portfolio.code
        )
        return positions
    }

    private fun getValuationData(positions: Positions, assets: Collection<AssetInput>): ValuationData {
        val futureFxResponse = asyncMdService.getFxData(
            fxUtils.buildRequest(
                positions.portfolio.base,
                positions
            )
        )
        val futurePriceResponse = asyncMdService.getMarketData(
            PriceRequest(positions.asAt, assets)
        )
        return ValuationData(
            futurePriceResponse[180, TimeUnit.SECONDS],
            futureFxResponse[30, TimeUnit.SECONDS]
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(PositionValuationService::class.java)
    }
}
