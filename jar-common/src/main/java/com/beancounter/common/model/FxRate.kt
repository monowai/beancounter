package com.beancounter.common.model

import org.springframework.boot.context.properties.ConstructorBinding
import java.math.BigDecimal

data class FxRate @ConstructorBinding constructor(
        val from: Currency,
        val to: Currency,
        val rate: BigDecimal?,
        val date: String?
)