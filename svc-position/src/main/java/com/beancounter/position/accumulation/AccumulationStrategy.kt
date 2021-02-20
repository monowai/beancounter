package com.beancounter.position.accumulation

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn

interface AccumulationStrategy {
    fun accumulate(trn: Trn, portfolio: Portfolio, position: Position)
}
