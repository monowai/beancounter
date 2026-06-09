package com.beancounter.position.valuation

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils

/**
 * Valuation services are responsible for computing
 * the market value of Positions.
 *
 * @author mikeh
 * @since 2019-02-24
 */
interface Valuation {
    fun build(
        portfolio: Portfolio,
        valuationDate: String
    ): PositionResponse

    fun build(trnQuery: TrustedTrnQuery): PositionResponse

    fun getPositions(
        portfolio: Portfolio,
        valuationDate: String = DateUtils.TODAY,
        value: Boolean
    ): PositionResponse

    /**
     * Aggregates positions across all provided portfolios into a single view.
     * Positions for the same asset from different portfolios are combined.
     *
     * @param portfolios collection of portfolios to aggregate
     * @param valuationDate date for position valuation
     * @param value whether to include market values
     * @param targetCurrencyCode optional ISO currency code (e.g. "SGD") to use
     *   as the PORTFOLIO reporting currency. When supplied, the synthesised
     *   context portfolio adopts this currency so MarketValue prices each
     *   bucket directly against the user's requested currency — avoiding the
     *   FX round-trip drift that surfaces when `portfolios.first().currency`
     *   differs from the caller's target. PRIVATE / POLICY positions are
     *   most sensitive (constant `close=1`) because the round-trip rate
     *   product isn't exactly 1.0.
     * @return aggregated positions from all portfolios
     */
    fun getAggregatedPositions(
        portfolios: Collection<Portfolio>,
        valuationDate: String = DateUtils.TODAY,
        value: Boolean,
        targetCurrencyCode: String? = null
    ): PositionResponse

    /**
     * Values positions. This should also set the Asset details as the caller has only
     * minimal knowledge.  MarketData contains asset and market details
     *
     * @param positions to value
     * @return positions with values and hydrated Asset objects
     */
    fun value(positions: Positions): PositionResponse
}