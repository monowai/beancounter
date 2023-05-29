package com.beancounter.common.model

import java.math.BigDecimal

/**
 * A Value, defaulting to ZERO.
 */
data class Totals(var total: BigDecimal = BigDecimal.ZERO)
