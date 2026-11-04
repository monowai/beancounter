package com.beancounter.common.model

import com.beancounter.common.utils.changePercent
import com.fasterxml.jackson.annotation.JsonProperty
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
    var irr: BigDecimal = BigDecimal.ZERO,
    /** Change on day across every position, in [currency]; zero in the TRADE view, which can mix currencies. */
    var gainOnDay: BigDecimal = BigDecimal.ZERO
) {
    /** [gainOnDay] against the value before it, in percentage points. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    fun getGainOnDayPercent(): BigDecimal = changePercent(gainOnDay, marketValue)
}