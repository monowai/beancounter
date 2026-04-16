package com.beancounter.agent.tools

import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// Privacy-preserving projections of portfolio/position data sent to the LLM.
// Absolute monetary amounts (market value, cost basis, unrealised/realised gain,
// dividends, averages) are intentionally excluded — the user's dollar balances
// never reach the LLM provider. Only ratios, weights, percentages, and public
// market prices are exposed.

/**
 * Portfolio metadata with no dollar balances. `irr` is a decimal ratio.
 */
data class ScrubbedPortfolio(
    val code: String,
    val name: String,
    val currency: String,
    val baseCurrency: String,
    val irr: Double?
)

/**
 * Single position expressed in ratios and public market data.
 *
 * `weight` is the portfolio weight (decimal, e.g. 0.125 = 12.5%).
 * `xirr` is annualised money-weighted return (decimal, 0.12 = 12% p.a.).
 * `roi` is total-return ratio (1.25 = +25%, 0.85 = -15%).
 * `yieldPercent` is dividends / marketValue (decimal), computed server-side.
 * Price fields are public market data and retained.
 */
data class ScrubbedPosition(
    val assetCode: String,
    val assetName: String?,
    val market: String,
    val category: String,
    val tradeCurrency: String,
    val weight: Double,
    val xirr: Double?,
    val roi: Double?,
    val changePercent: Double?,
    val yieldPercent: Double?,
    val priceClose: Double?,
    val priceDate: String?,
    val closed: Boolean
)

data class ScrubbedPositionResponse(
    val portfolioCode: String,
    val portfolioName: String,
    val baseCurrency: String,
    val asAt: String,
    val mixedCurrencies: Boolean,
    val overallIrr: Double?,
    val positions: List<ScrubbedPosition>
)

/**
 * Maps full domain models to their scrubbed projections.
 */
@Service
class ResponseScrubber {
    fun scrub(portfolio: Portfolio): ScrubbedPortfolio =
        ScrubbedPortfolio(
            code = portfolio.code,
            name = portfolio.name,
            currency = portfolio.currency.code,
            baseCurrency = portfolio.base.code,
            irr = portfolio.irr.toNullableDouble()
        )

    fun scrub(response: PortfoliosResponse): List<ScrubbedPortfolio> = response.data.map(::scrub)

    fun scrub(response: PositionResponse): ScrubbedPositionResponse {
        val positions = response.data
        return ScrubbedPositionResponse(
            portfolioCode = positions.portfolio.code,
            portfolioName = positions.portfolio.name,
            baseCurrency = positions.portfolio.base.code,
            asAt = positions.asAt,
            mixedCurrencies = positions.isMixedCurrencies,
            overallIrr =
                positions.totals[Position.In.PORTFOLIO]
                    ?.irr
                    ?.toNullableDouble(),
            positions = positions.positions.values.map(::scrubPosition)
        )
    }

    private fun scrubPosition(position: Position): ScrubbedPosition {
        val portfolioBucket = position.moneyValues[Position.In.PORTFOLIO]
        val price = portfolioBucket?.priceData
        val asset = position.asset
        val isClosed = position.quantityValues.getTotal().signum() == 0
        val yieldPercent =
            if (portfolioBucket == null ||
                portfolioBucket.marketValue.signum() == 0
            ) {
                null
            } else {
                portfolioBucket.dividends
                    .divide(portfolioBucket.marketValue, MATH)
                    .setScale(6, RoundingMode.HALF_UP)
                    .toDouble()
            }
        return ScrubbedPosition(
            assetCode = asset.code,
            assetName = asset.name,
            market = asset.market.code,
            category = asset.category,
            tradeCurrency =
                position.moneyValues[Position.In.TRADE]
                    ?.currency
                    ?.code ?: asset.market.currency.code,
            weight = portfolioBucket?.weight?.toDouble() ?: 0.0,
            xirr = portfolioBucket?.irr?.toNullableDouble(),
            roi = portfolioBucket?.roi?.toNullableDouble(),
            changePercent = price?.changePercent?.toNullableDouble(),
            yieldPercent = yieldPercent,
            priceClose = price?.close?.toNullableDouble(),
            priceDate = price?.priceDate?.toString(),
            closed = isClosed
        )
    }

    companion object {
        private val MATH = MathContext.DECIMAL64
    }
}

private fun BigDecimal?.toNullableDouble(): Double? = this?.toDouble()