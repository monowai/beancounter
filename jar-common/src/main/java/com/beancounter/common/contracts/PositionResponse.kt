package com.beancounter.common.contracts

import com.beancounter.common.model.Positions

/**
 * Response to a request.
 */
data class PositionResponse(override var data: Positions) : Payload<Positions>
