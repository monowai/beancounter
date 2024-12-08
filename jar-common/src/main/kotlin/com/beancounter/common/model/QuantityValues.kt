package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

/**
 * Accumulation of quantities.
 *
 * @author mikeh
 * @since 2019-01-28
 */
class QuantityValues(
    var sold: BigDecimal = BigDecimal.ZERO,
    var purchased: BigDecimal = BigDecimal.ZERO,
) {
    var adjustment: BigDecimal = BigDecimal.ZERO
    private var precision: Int? = null

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    fun getTotal(): BigDecimal = purchased.add(sold).add(adjustment)

    @JsonIgnore
    fun hasPosition() = getTotal().compareTo(BigDecimal.ZERO) != 0

    fun getPrecision(): Int {
        // This is a bit hacky. Should be derived from the asset and set not computed
        if (precision != null) {
            return precision as Int
        }
        return if (getTotal().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) 0 else 3
    }

    fun setPrecision(precision: Int = 2) {
        this.precision = precision
    }

    override fun toString(): String = getTotal().toString()
}
