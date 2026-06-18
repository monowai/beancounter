package com.beancounter.common.contracts

import com.beancounter.common.model.Trn

/**
 * Arguments to a valuation request.
 */
data class PositionRequest(
    val portfolioId: String,
    var trns: Collection<Trn>
)