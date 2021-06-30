package com.beancounter.common.utils

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Percentage related utils.
 */
@Service
class PercentUtils {
    private val percentScale = 6
    private val numberUtils = NumberUtils()

    fun percent(currentValue: BigDecimal?, oldValue: BigDecimal?): BigDecimal? {
        return percent(currentValue, oldValue, percentScale)
    }

    fun percent(previous: BigDecimal?, current: BigDecimal?, percentScale: Int): BigDecimal? {
        return if (numberUtils.isUnset(previous) || numberUtils.isUnset(current)) {
            null
        } else previous!!.divide(current, percentScale, RoundingMode.HALF_UP)
    }
}
