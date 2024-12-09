package com.beancounter.common.model

import java.math.BigDecimal

fun Portfolio.setMarketValue(
    marketValue: BigDecimal,
    irr: BigDecimal
): Portfolio =
    Portfolio(
        id = this.id,
        code = this.code,
        name = this.name,
        base = this.base,
        currency = this.currency,
        owner = this.owner,
        marketValue = marketValue,
        irr = irr
    )