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

    fun percent(
        currentValue: BigDecimal?,
        oldValue: BigDecimal?
    ): BigDecimal =
        percent(
            currentValue,
            oldValue,
            percentScale
        )

    fun percent(
        previous: BigDecimal?,
        current: BigDecimal?,
        percentScale: Int
    ): BigDecimal =
        if (numberUtils.isUnset(previous) || numberUtils.isUnset(current)) {
            BigDecimal.ZERO
        } else {
            previous!!.divide(
                current,
                percentScale,
                RoundingMode.HALF_UP
            )
        }
}