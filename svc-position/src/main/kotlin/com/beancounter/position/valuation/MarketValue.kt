package com.beancounter.position.valuation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MoneyValues
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
        rates: Map<IsoCurrencyPair, FxRate>
    ): Position {
        val asset = marketData.asset
        val trade = asset.market.currency
        val position = positions[asset]
        val portfolio = positions.portfolio
        val total = position.quantityValues.getTotal()
        value(
            total, position.getMoneyValues(Position.In.TRADE, asset.market.currency),
            marketData,
            FxRate(
                marketData.asset.market.currency, marketData.asset.market.currency,
                BigDecimal.ONE, null
            )
        )
        value(
            total, position.getMoneyValues(Position.In.BASE, portfolio.base),
            marketData,
            rate(portfolio.base, trade, rates)
        )
        value(
            total, position.getMoneyValues(Position.In.PORTFOLIO, portfolio.currency),
            marketData,
            rate(portfolio.currency, trade, rates)
        )
        return position
    }

    private fun value(total: BigDecimal, moneyValues: MoneyValues, mktData: MarketData, rate: FxRate) {
        moneyValues.priceData = of(mktData, rate.rate)
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            moneyValues.marketValue = BigDecimal.ZERO
        } else {
            var close = BigDecimal.ZERO
            if (moneyValues.priceData != null && moneyValues.priceData!!.close != null) {
                close = moneyValues.priceData!!.close
            }
            moneyValues.marketValue = Objects.requireNonNull(
                multiply(close, total)
            )!!
        }
        gains.value(total, moneyValues)
    }

    private fun rate(report: Currency, trade: Currency, rates: Map<IsoCurrencyPair, FxRate>): FxRate {
        return if (report.code == trade.code) {
            FxRate(trade, report, BigDecimal.ONE, null)
        } else rates[toPair(report, trade)]
            ?: throw BusinessException(
                String.format(
                    "No rate for %s:%s",
                    report.code,
                    trade.code
                )
            )
    }
}
