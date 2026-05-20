package com.beancounter.agent.tools

import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import org.springframework.stereotype.Service
import java.math.BigDecimal

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
 * Compact, columnar projection of positions for an LLM tool result.
 *
 * Field names live in [COLS] once. Each entry of [rows] is a positional list
 * aligned to [COLS] — typically ~75% smaller than the equivalent array of
 * named-field objects, and tool results are never cached, so the saving
 * applies on every chat turn.
 *
 * Closed (zero-quantity) positions are filtered out before this DTO is
 * built, so every row represents an open position. The legacy `closed`
 * column has been dropped accordingly.
 *
 * Column meanings (decimals throughout for ratios):
 *   assetCode, assetName, market   — public identifiers
 *   priceClose                     — public market price
 *   changePercent                  — today's price move (0.012 = +1.2%)
 *   xirr                           — annualised money-weighted return
 *   weight                         — portfolio weight (0.125 = 12.5%)
 *   category                       — asset class
 *   opened                         — ISO date this position opened (YYYY-MM-DD,
 *                                    cleared on close, reset on reopen)
 *   lastTrade                      — ISO date of most recent transaction
 *   lastDividend                   — ISO date of most recent dividend
 */
data class ScrubbedPositionResponse(
    val portfolioCode: String,
    val portfolioName: String,
    val baseCurrency: String,
    val asAt: String,
    val mixedCurrencies: Boolean,
    val overallIrr: Double?,
    val cols: List<String> = COLS,
    val rows: List<List<Any?>>
) {
    companion object {
        val COLS: List<String> =
            listOf(
                "assetCode",
                "assetName",
                "market",
                "priceClose",
                "changePercent",
                "xirr",
                "weight",
                "category",
                "opened",
                "lastTrade",
                "lastDividend"
            )
    }
}

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
        // Drop closed (zero-quantity) rows server-side so the LLM never sees
        // them. Earlier the system prompt asked the LLM to filter silently;
        // it kept surfacing them in commentary anyway. Excluding here is
        // both cheaper (smaller payload) and behaviourally reliable.
        val openRows =
            positions.positions.values
                .filter { it.quantityValues.getTotal().signum() != 0 }
                .map(::scrubPositionRow)
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
            rows = openRows
        )
    }

    private fun scrubPositionRow(position: Position): List<Any?> {
        val portfolioBucket = position.moneyValues[Position.In.PORTFOLIO]
        val price = portfolioBucket?.priceData
        val asset = position.asset
        return listOf(
            asset.code,
            asset.name,
            asset.market.code,
            price?.close?.toNullableDouble(),
            price?.changePercent?.toNullableDouble(),
            portfolioBucket?.irr?.toNullableDouble(),
            portfolioBucket?.weight?.toDouble() ?: 0.0,
            asset.category,
            position.dateValues.opened?.toString(),
            position.dateValues.last?.toString(),
            position.dateValues.lastDividend?.toString()
        )
    }
}

private fun BigDecimal?.toNullableDouble(): Double? = this?.toDouble()