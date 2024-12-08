package com.beancounter.common.contracts

import com.beancounter.common.model.Positions

/**
 * Response to a request.
 */
data class PositionResponse(
    override val data: Positions = Positions(),
) : Payload<Positions>
