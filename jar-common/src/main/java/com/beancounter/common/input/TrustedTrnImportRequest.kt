package com.beancounter.common.input

import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio

data class TrustedTrnImportRequest(override var portfolio: Portfolio,
                              var callerRef: CallerRef?,
                              override var message: String?,
                              val row: List<String>) : TrnImport {
    constructor(portfolio: Portfolio, row: List<String>)
            : this(portfolio, null, null, row)
}