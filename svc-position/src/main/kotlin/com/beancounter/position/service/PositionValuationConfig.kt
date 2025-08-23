package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.irr.IrrCalculator
import com.beancounter.position.utils.FxUtils
import com.beancounter.position.valuation.MarketValue
import org.springframework.stereotype.Component

/**
 * Configuration class that groups related services for position valuation.
 * This reduces the number of constructor parameters in PositionValuationService.
 */
@Component
data class PositionValuationConfig(
    val marketValue: MarketValue,
    val fxUtils: FxUtils,
    val priceService: PriceService,
    val fxRateService: FxService,
    val tokenService: TokenService,
    val dateUtils: DateUtils,
    val irrCalculator: IrrCalculator
)