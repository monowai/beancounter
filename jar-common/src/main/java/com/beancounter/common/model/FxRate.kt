package com.beancounter.common.model

import org.springframework.boot.context.properties.ConstructorBinding
import java.math.BigDecimal

/**
 * Representation of an FX Rate on a given date.
 */
data class FxRate @ConstructorBinding constructor(
    val from: Currency,
    val to: Currency,
    val rate: BigDecimal = BigDecimal.ONE,
    val date: String?
)
