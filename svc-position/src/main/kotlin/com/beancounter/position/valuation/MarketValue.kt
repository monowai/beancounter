package com.beancounter.position.valuation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.PriceData.Companion.of
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils.Companion.multiply
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.Objects

/**
 * Compute the market value and accumulate gains
 */
@Service
class MarketValue(
    private val gains: Gains,
    private val dateUtils: DateUtils = DateUtils(),
    private val cashUtils: CashUtils = CashUtils()
) {
    fun value(
        positions: Positions,
        marketData: MarketData,
        rates: Map<IsoCurrencyPair, FxRate>
    ): Position {
        val asset = marketData.asset
        val position = positions.getOrCreate(asset)

        val portfolio = positions.portfolio
        val isCash = cashUtils.isCash(asset)
        val tradeCurrency = position.getMoneyValues(Position.In.TRADE).currency

        // Loop through each valuation context to apply values
        val valuationContexts =
            listOf(
                Position.In.TRADE to tradeCurrency,
                Position.In.BASE to portfolio.base,
                Position.In.PORTFOLIO to portfolio.currency
            )
        valuationContexts.forEach { (context, currency) ->
            val rate =
                if (context == Position.In.TRADE) {
                    FxRate(
                        tradeCurrency,
                        tradeCurrency,
                        BigDecimal.ONE,
                        dateUtils.getDate(positions.asAt)
                    )
                } else {
                    rate(
                        tradeCurrency,
                        currency,
                        positions.asAt,
                        rates
                    )
                }

            value(
                position,
                context,
                marketData,
                rate,
                isCash
            )
        }

        return position
    }

    private fun value(
        position: Position,
        positionIn: Position.In,
        mktData: MarketData,
        rate: FxRate,
        isCash: Boolean
    ) {
        val total = position.quantityValues.getTotal()
        val moneyValues = position.getMoneyValues(positionIn)

        moneyValues.priceData =
            of(
                mktData,
                rate.rate
            )
        if (total.signum() == 0) {
            moneyValues.marketValue = BigDecimal.ZERO
        } else {
            val close = moneyValues.priceData.close
            moneyValues.marketValue =
                Objects.requireNonNull(
                    multiply(
                        close,
                        total
                    )
                )!!

            if (!isCash && moneyValues.priceData.previousClose.signum() != 0) {
                // Only calculate gainOnDay when there's valid previous close data
                moneyValues.gainOnDay =
                    (close.subtract(moneyValues.priceData.previousClose)).multiply(total)
            }
        }
        if (isCash) {
            moneyValues.realisedGain = BigDecimal.ZERO // Will figure this out later
            moneyValues.unrealisedGain = BigDecimal.ZERO // moneyValues.marketValue.subtract(moneyValues.costBasis)
            moneyValues.totalGain = BigDecimal.ZERO
            // moneyValues.realisedGain.add(moneyValues.unrealisedGain) // moneyValues.marketValue
            // Signed earmark, priced with the SAME per-bucket close as marketValue so
            // marketValue + earmarked = nominal in the same currency. Zero-guard: MathUtils
            // .multiply(x, ZERO) returns x (treats a zero rate as "unset"), NOT 0.
            moneyValues.earmarked =
                if (position.earmarkedQuantity.signum() == 0) {
                    BigDecimal.ZERO
                } else {
                    multiply(moneyValues.priceData.close, position.earmarkedQuantity) ?: BigDecimal.ZERO
                }
        } else if (mktData.asset.assetCategory.id == "POLICY" &&
            moneyValues.costBasis.signum() == 0
        ) {
            // Legacy composite/pension positions reached via DEPOSIT trns
            // carry no cost basis (CashCost cost-tracking is disabled), so a
            // straight MV-vs-cost calc would print a phantom 100% gain. Pin
            // cost = MV for that case only. BalanceBehaviour pins cost on the
            // first BALANCE snapshot, so subsequent snapshots fall through to
            // gains.value below and the real gain/loss surfaces.
            moneyValues.costBasis = moneyValues.marketValue
            moneyValues.costValue = moneyValues.marketValue
            moneyValues.realisedGain = BigDecimal.ZERO
            moneyValues.unrealisedGain = BigDecimal.ZERO
            moneyValues.totalGain = BigDecimal.ZERO
        } else {
            gains.value(
                total,
                moneyValues
            )
        }
    }

    private fun rate(
        from: Currency,
        to: Currency,
        asAt: String,
        rates: Map<IsoCurrencyPair, FxRate>
    ): FxRate =
        if (from.code == to.code) {
            FxRate(
                from,
                to,
                BigDecimal.ONE,
                dateUtils.getDate(asAt)
            )
        } else {
            rates[
                IsoCurrencyPair(
                    from.code,
                    to.code
                )
            ]
                ?: throw BusinessException("No rate available for ${from.code} to ${to.code}")
        }
}