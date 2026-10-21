package com.beancounter.agent.holding

import com.beancounter.agent.PageQueries
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * One open holding as svc-position values it, for the opening turn of a
 * Holding Analysis — in ratios and unit prices only. The model never sees
 * what the holding is worth: no quantity, value, cost or money gain, the
 * same rule [com.beancounter.agent.tools.ResponseScrubber] keeps for tools.
 *
 * [weight] is the PORTFOLIO-bucket weight, a single-currency figure. Unit
 * prices — [averageCost] and [currentPrice] — both come from the TRADE
 * bucket, so the entry-versus-now comparison is in one currency, the asset's
 * [tradeCurrency]. [unrealisedGainRatio]
 * is svc-position's unrealised gain over its cost, both in the portfolio
 * currency; null when there is no cost to measure it against.
 */
data class HoldingFacts(
    val portfolioCode: String,
    val portfolioName: String,
    val portfolioCurrency: String,
    val assetCode: String,
    val assetName: String,
    val market: String,
    val tradeCurrency: String,
    val weight: BigDecimal,
    val averageCost: BigDecimal,
    val currentPrice: BigDecimal?,
    val priceDate: LocalDate?,
    val unrealisedGainRatio: BigDecimal?,
    val opened: LocalDate?
) {
    companion object {
        private const val MIN_PRICE_SCALE = 2
        private const val MAX_PRICE_SCALE = 4
        private const val PERCENT_SCALE = 1
        private const val RATIO_SCALE = 6
        private val WHITESPACE = Regex("\\s+")

        /** [gain] over [cost], or null when there is no cost to measure it against. */
        fun ratio(
            gain: BigDecimal,
            cost: BigDecimal
        ): BigDecimal? = if (cost.signum() > 0) gain.divide(cost, RATIO_SCALE, RoundingMode.HALF_UP) else null
    }

    /**
     * A compact block of facts, one per line, ready for a prompt. Names are
     * free text, so each is kept to one line with no fence tags in it.
     */
    fun describe(): String =
        buildList {
            add("- Portfolio: $portfolioCode (${plain(portfolioName)}), portfolio currency $portfolioCurrency")
            add("- Asset: $assetCode (${plain(assetName)}) on $market, trade currency $tradeCurrency")
            add("- Weight in portfolio: ${percent(weight)} (measured in $portfolioCurrency)")
            add("- Average cost: ${price(averageCost)} $tradeCurrency per unit")
            currentPrice?.let {
                add("- Current price: ${price(it)} $tradeCurrency${priceDate?.let { d -> " ($d)" }.orEmpty()}")
            }
            unrealisedGainRatio?.let { add("- Unrealised gain: ${percent(it)} on cost") }
            opened?.let { add("- Opened: $it") }
        }.joinToString("\n")

    private fun price(value: BigDecimal): String =
        value
            .setScale(
                value.stripTrailingZeros().scale().coerceIn(MIN_PRICE_SCALE, MAX_PRICE_SCALE),
                RoundingMode.HALF_UP
            ).toPlainString()

    private fun plain(name: String): String = PageQueries.unfenced(name).replace(WHITESPACE, " ").trim()

    /** [ratio] is a fraction: 0.125 reads as 12.5%. */
    private fun percent(ratio: BigDecimal): String =
        ratio.movePointRight(2).setScale(PERCENT_SCALE, RoundingMode.HALF_UP).toPlainString() + "%"
}