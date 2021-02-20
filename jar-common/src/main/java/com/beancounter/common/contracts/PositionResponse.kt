package com.beancounter.common.contracts

import com.beancounter.common.model.Positions

data class PositionResponse(override var data: Positions) : Payload<Positions>
