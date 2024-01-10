package com.beancounter.common.model

import java.math.BigDecimal

/**
 * Summary Money Values used for financial calculations.
 *
 * @author mikeh
 * @since 2019-01-28
 */
data class MoneyValues(val currency: Currency) {
    var dividends: BigDecimal = BigDecimal.ZERO

    // Cost in Currency terms
    var costValue: BigDecimal = BigDecimal.ZERO

    // Internal accounting - tracks the basis used for computing cost.
    var costBasis: BigDecimal = BigDecimal.ZERO
    var fees: BigDecimal = BigDecimal.ZERO
    var purchases: BigDecimal = BigDecimal.ZERO
    var sales: BigDecimal = BigDecimal.ZERO
    var marketValue: BigDecimal = BigDecimal.ZERO
    var weight: BigDecimal? = null
    var priceData: PriceData? = null
    var averageCost: BigDecimal = BigDecimal.ZERO

    /**
     * How much gain has been realised for the position.
     */
    var realisedGain: BigDecimal = BigDecimal.ZERO
    var unrealisedGain: BigDecimal = BigDecimal.ZERO
    var totalGain: BigDecimal = BigDecimal.ZERO
    var gainOnDay: BigDecimal = BigDecimal.ZERO
}
