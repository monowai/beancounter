package com.beancounter.position.service

import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse

/**
 * Supports various calls to get Postion related data.
 *
 * @author mikeh
 * @since 2019-01-27
 */
interface Position {
    /**
     * Return the position collection from a collection of transactions.
     *
     * @param positionRequest Data to value
     * @return computed stock positions
     */
    fun build(positionRequest: PositionRequest): PositionResponse
}
