package com.beancounter.common.input

import com.beancounter.common.model.Portfolio

data class TrustedTrnEvent(override val portfolio: Portfolio, override val message: String?, val trnInput: TrnInput)
    : TrnImport {
    constructor(portfolio: Portfolio, trnInput: TrnInput) : this(portfolio, null, trnInput)

}