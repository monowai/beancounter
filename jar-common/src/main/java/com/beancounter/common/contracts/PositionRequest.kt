package com.beancounter.common.contracts

import com.beancounter.common.model.Trn

data class PositionRequest(val portfolioId: String, var trns: Collection<Trn>)