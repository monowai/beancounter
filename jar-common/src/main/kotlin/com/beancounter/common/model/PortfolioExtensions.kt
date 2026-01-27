package com.beancounter.common.model

import java.math.BigDecimal
import java.time.LocalDate

fun Portfolio.setMarketValue(
    marketValue: BigDecimal,
    irr: BigDecimal,
    gainOnDay: BigDecimal = BigDecimal.ZERO,
    assetClassification: Map<String, BigDecimal> = emptyMap(),
    valuedAt: LocalDate? = null
): Portfolio =
    Portfolio(
        id = this.id,
        code = this.code,
        name = this.name,
        base = this.base,
        currency = this.currency,
        owner = this.owner,
        marketValue = marketValue,
        irr = irr,
        gainOnDay = gainOnDay,
        assetClassification = assetClassification,
        valuedAt = valuedAt,
        lastUpdated = this.lastUpdated
    )