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
    private val rateScale = 4
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

    /**
     * Scale a rate value to consistent precision to avoid floating point serialization issues.
     * Uses 4 decimal places (e.g., 0.0700 for 7%).
     */
    fun scaleRate(value: BigDecimal): BigDecimal = value.setScale(rateScale, RoundingMode.HALF_UP)

    /**
     * Scale a percentage value to 2 decimal places.
     * Use this when storing/returning percentage values directly (e.g., 7.11 for 7.11%).
     */
    fun scalePercent(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)
}