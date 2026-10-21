package com.beancounter.agent.holding

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * One open holding as svc-position values it, for the opening turn of a
 * Holding Analysis. Unit prices are in the asset's [tradeCurrency]; the
 * weight and every amount are in the [portfolioCurrency], where the weight
 * is measured — never a TRADE-currency figure.
 */
data class HoldingFacts(
    val portfolioCode: String,
    val portfolioName: String,
    val portfolioCurrency: String,
    val assetCode: String,
    val assetName: String,
    val market: String,
    val tradeCurrency: String,
    val quantity: BigDecimal,
    val weight: BigDecimal,
    val averageCost: BigDecimal,
    val currentPrice: BigDecimal?,
    val priceDate: LocalDate?,
    val costBasis: BigDecimal,
    val marketValue: BigDecimal,
    val unrealisedGain: BigDecimal,
    val realisedGain: BigDecimal,
    val opened: LocalDate?
) {
    private companion object {
        const val MONEY_SCALE = 2
        const val MIN_PRICE_SCALE = 2
        const val MAX_PRICE_SCALE = 4
        const val PERCENT_SCALE = 1
        const val RATIO_SCALE = 6
    }

    /** A compact block of facts, one per line, ready for a prompt. */
    fun describe(): String =
        buildList {
            add("- Portfolio: $portfolioCode ($portfolioName), portfolio currency $portfolioCurrency")
            add("- Asset: $assetCode ($assetName) on $market, trade currency $tradeCurrency")
            add("- Quantity held: ${quantity.stripTrailingZeros().toPlainString()}")
            add("- Weight in portfolio: ${percent(weight)} (measured in $portfolioCurrency)")
            add("- Average cost: ${price(averageCost)} $tradeCurrency per unit")
            currentPrice?.let {
                add(
                    "- Current price: ${price(it)} $tradeCurrency${priceDate?.let { d ->
                        " ($d)"
                    }.orEmpty()}"
                )
            }
            add("- Cost basis: ${money(costBasis)} $portfolioCurrency")
            add("- Market value: ${money(marketValue)} $portfolioCurrency")
            add("- Unrealised gain: ${money(unrealisedGain)} $portfolioCurrency${onCost()}")
            add("- Realised gain: ${money(realisedGain)} $portfolioCurrency")
            opened?.let { add("- Opened: $it") }
        }.joinToString("\n")

    private fun onCost(): String =
        if (costBasis.signum() > 0) {
            " (${percent(unrealisedGain.divide(costBasis, RATIO_SCALE, RoundingMode.HALF_UP))} on cost)"
        } else {
            ""
        }

    private fun money(value: BigDecimal): String = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString()

    private fun price(value: BigDecimal): String =
        value
            .setScale(
                value.stripTrailingZeros().scale().coerceIn(MIN_PRICE_SCALE, MAX_PRICE_SCALE),
                RoundingMode.HALF_UP
            ).toPlainString()

    private fun percent(ratio: BigDecimal): String =
        ratio.movePointRight(2).setScale(PERCENT_SCALE, RoundingMode.HALF_UP).toPlainString() + "%"
}