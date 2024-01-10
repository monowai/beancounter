package com.beancounter.position.valuation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.PriceData.Companion.of
import com.beancounter.common.utils.MathUtils.Companion.multiply
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.Objects

/**
 * Compute the market value and accumulate gains
 */
@Service
class MarketValue(private val gains: Gains) {
    fun value(
        positions: Positions,
        marketData: MarketData,
        rates: Map<IsoCurrencyPair, FxRate>,
    ): Position {
        val asset = marketData.asset
        // Wrong
        if (!positions.contains(asset)) {
            throw BusinessException("Unable to find $asset in the supplied positions")
        }
        val position = positions[asset]
        val portfolio = positions.portfolio
        val isCash = asset.market.code == "CASH"
        val trade = position.getMoneyValues(Position.In.TRADE).currency
        value(
            position,
            Position.In.TRADE,
            marketData,
            FxRate(
                trade,
                trade,
                BigDecimal.ONE,
                positions.asAt,
            ),
            isCash,
        )
        value(
            position,
            Position.In.BASE,
            marketData,
            rate(trade, portfolio.base, rates),
            isCash,
        )
        value(
            position,
            Position.In.PORTFOLIO,
            marketData,
            rate(trade, portfolio.currency, rates),
            isCash,
        )
        return position
    }

    private fun value(
        position: Position,
        positionIn: Position.In,
        mktData: MarketData,
        rate: FxRate,
        isCash: Boolean,
    ) {
        val total = position.quantityValues.getTotal()
        val moneyValues = position.getMoneyValues(positionIn)

        moneyValues.priceData = of(mktData, rate.rate)
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            moneyValues.marketValue = BigDecimal.ZERO
        } else {
            val close: BigDecimal
            if (moneyValues.priceData!!.close != null) {
                close = moneyValues.priceData!!.close!!
                moneyValues.marketValue =
                    Objects.requireNonNull(
                        multiply(close, total),
                    )!!

                if (moneyValues.priceData!!.previousClose != null && !isCash) {
                    moneyValues.gainOnDay = (close.subtract(moneyValues.priceData!!.previousClose)).multiply(total)
                }
            }
        }
        if (isCash) {
            moneyValues.realisedGain = BigDecimal.ZERO // Will figure this out later
            moneyValues.unrealisedGain = moneyValues.marketValue.subtract(moneyValues.costBasis)
            moneyValues.totalGain = moneyValues.realisedGain.add(moneyValues.unrealisedGain) // moneyValues.marketValue
            // moneyValues.costValue = total.multiply(moneyValues.averageCost)
            // moneyValues.costValue = moneyValues.marketValue
        } else {
            gains.value(total, moneyValues)
        }
    }

    private fun rate(
        from: Currency,
        to: Currency,
        rates: Map<IsoCurrencyPair, FxRate>,
    ): FxRate {
        return if (from.code == to.code) {
            FxRate(to, from, BigDecimal.ONE)
        } else {
            rates[toPair(from, to)]
                ?: throw BusinessException(
                    String.format(
                        "No rate for ${from.code}:${to.code}",
                    ),
                )
        }
    }
}
