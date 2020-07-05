package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

/**
 * Accumulation of quantities.
 *
 * @author mikeh
 * @since 2019-01-28
 */
class QuantityValues {
    var sold = BigDecimal.ZERO
    var purchased = BigDecimal.ZERO
    var adjustment = BigDecimal.ZERO
    private var precision: Int? = null

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    fun getTotal(): BigDecimal {
        return purchased.add(sold).add(adjustment)
    }

    fun getPrecision(): Int {
        // This is a bit hacky. Should be derived from the asset and set not computed
        if ( precision != null ){
            return precision as Int
        }
        return if (getTotal().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) 0 else 3
    }

    fun setPrecision(precision: Int) {
        this.precision = precision
    }

}