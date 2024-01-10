package com.beancounter.common.model

import com.beancounter.common.utils.DateUtils
import java.math.BigDecimal

/**
 * Representation of an FX Rate on a given date.
 */
data class FxRate(
    val from: Currency,
    val to: Currency,
    val rate: BigDecimal = BigDecimal.ONE,
    val date: String = DateUtils.TODAY,
)
