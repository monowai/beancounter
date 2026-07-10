package com.beancounter.common.model

import java.math.BigDecimal

/**
 * Summary Money Values used for financial calculations.
 *
 * @author mikeh
 * @since 2019-01-28
 */
data class MoneyValues(
    val currency: Currency
) {
    var dividends: BigDecimal = BigDecimal.ZERO
    var expenses: BigDecimal = BigDecimal.ZERO

    // Cost in Currency terms
    var costValue: BigDecimal = BigDecimal.ZERO

    // Internal accounting - tracks the basis used for computing cost.
    var costBasis: BigDecimal = BigDecimal.ZERO
    var fees: BigDecimal = BigDecimal.ZERO
    var purchases: BigDecimal = BigDecimal.ZERO
    var sales: BigDecimal = BigDecimal.ZERO
    var marketValue: BigDecimal = BigDecimal.ZERO

    // Cash reserved by PROPOSED (unsettled) cash legs that will move this cash
    // asset once they settle. Signed and in the same currency/bucket as
    // marketValue, so marketValue + earmarked = the "nominal" balance. Zero for
    // non-cash positions.
    var earmarked: BigDecimal = BigDecimal.ZERO
    var weight: BigDecimal = BigDecimal.ZERO
    var irr: BigDecimal = BigDecimal.ZERO
    var roi: BigDecimal = BigDecimal.ZERO
    var priceData: PriceData = PriceData()
    var averageCost: BigDecimal = BigDecimal.ZERO

    /**
     * How much gain has been realised for the position.
     */
    var realisedGain: BigDecimal = BigDecimal.ZERO
    var unrealisedGain: BigDecimal = BigDecimal.ZERO
    var totalGain: BigDecimal = BigDecimal.ZERO
    var gainOnDay: BigDecimal = BigDecimal.ZERO

    fun resetCosts() {
        averageCost = BigDecimal.ZERO
        costValue = BigDecimal.ZERO
        costBasis = BigDecimal.ZERO
    }
}