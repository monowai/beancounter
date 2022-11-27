package com.beancounter.common.model

import java.math.BigDecimal

/**
 * Representation of an FX Rate on a given date.
 */
data class FxRate constructor(
    val from: Currency,
    val to: Currency,
    val rate: BigDecimal = BigDecimal.ONE,
    val date: String?
)
