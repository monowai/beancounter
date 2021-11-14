package com.beancounter.position.service

import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Portfolio

/**
 * Supports various calls to get Position related data.
 *
 * @author mikeh
 * @since 2019-01-27
 */
interface Position {
    /**
     * Return the position collection from a collection of transactions.
     *
     * @param portfolio - for...
     * @param positionRequest Data to value
     * @return computed stock positions
     */
    fun build(portfolio: Portfolio, positionRequest: PositionRequest): PositionResponse
}
