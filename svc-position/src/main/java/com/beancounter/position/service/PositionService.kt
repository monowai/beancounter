package com.beancounter.position.service

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Positions
import org.springframework.stereotype.Service

/**
 * Returns collections of positions for a Portfolio.
 *
 * @author mikeh
 * @since 2019-02-01
 */
@Service
class PositionService internal constructor(
    private val accumulator: Accumulator,
    private val portfolioService: PortfolioServiceClient
) : Position {
    override fun build(positionRequest: PositionRequest): PositionResponse {
        return build(
            portfolioService.getPortfolioById(positionRequest.portfolioId),
            positionRequest
        )
    }

    fun build(portfolio: Portfolio, positionRequest: PositionRequest): PositionResponse {
        val positions = Positions(portfolio)
        for (trn in positionRequest.trns) {
            positions.add(accumulator.accumulate(trn, positions))
        }
        return PositionResponse(positions)
    }
}
