package com.beancounter.common.model

import java.math.BigDecimal

/**
 * A Value, defaulting to ZERO.
 */
data class Totals constructor(var total: BigDecimal = BigDecimal.ZERO)
