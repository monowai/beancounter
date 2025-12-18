package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.irr.IrrCalculator
import com.beancounter.position.utils.FxUtils
import com.beancounter.position.valuation.MarketValue
import org.springframework.beans.factory.annotation.Value
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
    val irrCalculator: IrrCalculator,
    /**
     * Minimum holding period (in days) before using IRR (XIRR) instead of simple ROI.
     *
     * Industry best practice is to use simple ROI for short-term investments (< 1 year)
     * because XIRR can produce extreme/misleading annualized values for short periods.
     * For investments held longer than this threshold, XIRR provides a more meaningful
     * time-weighted return that accounts for the timing of cash flows.
     *
     * Can be configured via BEANCOUNTER_IRR environment variable or beancounter.irr property.
     * Default: 365 days (1 year)
     */
    @param:Value("\${beancounter.irr:365}")
    val minHoldingDaysForIrr: Long = 365L,
    /**
     * Whether to include zero-quantity positions when fetching prices.
     *
     * When false (default), positions with zero quantity (sold out) are excluded from
     * price requests, reducing unnecessary API calls to market data providers.
     * When true, prices are fetched for all positions including those fully sold.
     *
     * Can be configured via BEANCOUNTER_VALUATION_INCLUDE_ZERO_QUANTITY environment variable
     * or beancounter.valuation.includeZeroQuantity property.
     * Default: false
     */
    @param:Value("\${beancounter.valuation.includeZeroQuantity:false}")
    val includeZeroQuantity: Boolean = false
)