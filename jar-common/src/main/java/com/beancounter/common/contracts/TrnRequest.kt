package com.beancounter.common.contracts

import com.beancounter.common.input.TrnInput

data class TrnRequest(var portfolioId: String, override var data: Collection<TrnInput>) : Payload<Collection<TrnInput>>

