package com.beancounter.common.model

import java.math.BigDecimal

/**
 * A Value, defaulting to ZERO.
 */
data class Totals(
    val currency: Currency,
    var marketValue: BigDecimal = BigDecimal.ZERO,
    var purchases: BigDecimal = BigDecimal.ZERO,
    var sales: BigDecimal = BigDecimal.ZERO,
    var cash: BigDecimal = BigDecimal.ZERO,
    var income: BigDecimal = BigDecimal.ZERO,
    var gain: BigDecimal = BigDecimal.ZERO,
)
