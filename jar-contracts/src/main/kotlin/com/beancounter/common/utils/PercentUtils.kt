package com.beancounter.common.utils

import com.beancounter.common.utils.MathUtils.Companion.HUNDRED
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

private const val PERCENT_SCALE = 6
private const val RATE_SCALE = 4
private const val POINTS_SCALE = 2

/**
 * Percentage related utils.
 */
@Service
class PercentUtils {
    private val numberUtils = NumberUtils()

    fun percent(
        currentValue: BigDecimal?,
        oldValue: BigDecimal?
    ): BigDecimal =
        percent(
            currentValue,
            oldValue,
            PERCENT_SCALE
        )

    fun percent(
        previous: BigDecimal?,
        current: BigDecimal?,
        percentScale: Int
    ): BigDecimal =
        if (numberUtils.isUnset(previous) || numberUtils.isUnset(current)) {
            BigDecimal.ZERO
        } else {
            requireNotNull(previous).divide(
                current,
                percentScale,
                RoundingMode.HALF_UP
            )
        }

    /**
     * Scale a rate value to consistent precision to avoid floating point serialization issues.
     * Uses 4 decimal places (e.g., 0.0700 for 7%).
     */
    fun scaleRate(
        value: BigDecimal,
        rateScale: Int = RATE_SCALE
    ): BigDecimal = value.setScale(rateScale, RoundingMode.HALF_UP)

    /**
     * Scale a percentage value to requested decimal places.
     * Use this when storing/returning percentage values directly (e.g., 7.11 for 7.11%).
     */
    fun scalePercent(
        value: BigDecimal,
        scale: Int = 2
    ): BigDecimal = value.setScale(scale, RoundingMode.HALF_UP)
}

/** [value] as a share of [total], in percentage points at two decimals. Zero when [total] is zero. */
fun percentOf(
    value: BigDecimal,
    total: BigDecimal
): BigDecimal =
    if (total.signum() == 0) {
        BigDecimal.ZERO.setScale(POINTS_SCALE)
    } else {
        value.multiply(HUNDRED).divide(total, POINTS_SCALE, RoundingMode.HALF_UP)
    }

/** A day's [change] against the value before it, [current] less the change, in percentage points. */
fun changePercent(
    change: BigDecimal,
    current: BigDecimal
): BigDecimal = percentOf(change, current.subtract(change))