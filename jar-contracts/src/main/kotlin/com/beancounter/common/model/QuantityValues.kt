package com.beancounter.common.model

import com.beancounter.common.exception.BusinessException
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

private const val FRACTIONAL_PRECISION = 3

/**
 * Accumulation of quantities, and the rules for how they may change: what is bought, what is
 * disposed of (held negative in [sold]) and what splits have added or taken ([adjustment]).
 * The buckets change only through those rules.
 *
 * @author mikeh
 * @since 2019-01-28
 */
class QuantityValues(
    sold: BigDecimal = BigDecimal.ZERO,
    purchased: BigDecimal = BigDecimal.ZERO,
    adjustment: BigDecimal = BigDecimal.ZERO
) {
    var sold: BigDecimal = sold
        private set
    var purchased: BigDecimal = purchased
        private set
    var adjustment: BigDecimal = adjustment
        private set
    private var fixedPrecision: Int? = null

    // Derived from the total until set. Should come from the asset.
    var precision: Int
        get() =
            fixedPrecision
                ?: if (getTotal().remainder(BigDecimal.ONE).signum() == 0) 0 else FRACTIONAL_PRECISION
        set(value) {
            fixedPrecision = value
        }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    fun getTotal(): BigDecimal = purchased.add(sold).add(adjustment)

    fun buy(quantity: BigDecimal) {
        purchased = purchased.add(quantity)
    }

    /**
     * Takes [quantity] out of what is held, whichever way it is signed. More than is held is
     * rejected: a short position needs its own accounting.
     *
     * @return the quantity held before the disposal
     */
    fun dispose(quantity: BigDecimal): BigDecimal {
        val disposed = quantity.abs()
        val held = getTotal()
        if (disposed > held) {
            throw BusinessException("$disposed is more than the $held held; short positions are not supported")
        }
        sold = sold.subtract(disposed)
        return held
    }

    /** Takes [quantity] of cash out, whichever way it is signed. Cash may go below zero. */
    fun debit(quantity: BigDecimal) {
        sold = sold.subtract(quantity.abs())
    }

    /** Applies a split [ratio] to everything held, which already carries earlier splits. */
    fun applySplit(ratio: BigDecimal) {
        val total = getTotal()
        adjustment = adjustment.add(ratio.multiply(total).subtract(total))
    }

    /** An absolute [quantity] replaces whatever earlier changes built up. */
    fun resetTo(quantity: BigDecimal) {
        purchased = quantity
        sold = BigDecimal.ZERO
        adjustment = BigDecimal.ZERO
    }

    /** Adds [other] bucket by bucket, as when one asset is held in several portfolios. */
    fun add(other: QuantityValues) {
        purchased = purchased.add(other.purchased)
        sold = sold.add(other.sold)
        adjustment = adjustment.add(other.adjustment)
    }

    @JsonIgnore
    fun hasPosition() = getTotal().compareTo(BigDecimal.ZERO) != 0

    override fun toString(): String = getTotal().toString()
}