package com.beancounter.common.model

import java.math.BigDecimal

/**
 * Which way a watchlist target is reached, fixed when the target is set: [ABOVE] when the
 * target is at or above the reference price, else [BELOW].
 */
enum class TargetDirection {
    ABOVE,
    BELOW;

    /** True when [close] has reached [target] in this direction. */
    fun reached(
        close: BigDecimal,
        target: BigDecimal
    ): Boolean =
        when (this) {
            ABOVE -> close >= target
            BELOW -> close <= target
        }

    companion object {
        fun of(
            target: BigDecimal,
            reference: BigDecimal
        ): TargetDirection = if (target >= reference) ABOVE else BELOW
    }
}