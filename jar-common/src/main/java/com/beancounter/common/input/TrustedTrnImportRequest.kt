package com.beancounter.common.input

import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio

data class TrustedTrnImportRequest(
    override var portfolio: Portfolio,
    override val importFormat: ImportFormat = ImportFormat.BC,
    var callerRef: CallerRef = CallerRef(),
    override var message: String = "",
    val row: List<String> = emptyList(),
) : TrnImport {
    constructor(portfolio: Portfolio, row: List<String>, importFormat: ImportFormat = ImportFormat.BC) :
        this(portfolio, importFormat, message = "", row = row)
}
