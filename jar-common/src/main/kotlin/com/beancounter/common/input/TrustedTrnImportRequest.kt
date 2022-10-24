package com.beancounter.common.input

import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio

/**
 * Contract to write a transaction, transforming from an ImportFormat, into BC for the requested Portfolio.
 * Ownership of the portfolio is not verified, making this a trusted request.
 *
 * Message is optional and will be output to the log when the event is processed
 */
data class TrustedTrnImportRequest(
    override var portfolio: Portfolio,
    override val importFormat: ImportFormat = ImportFormat.BC,
    var callerRef: CallerRef = CallerRef(),
    override var message: String = "",
    val row: List<String> = emptyList()
) : TrnImport {
    constructor(portfolio: Portfolio, row: List<String>, importFormat: ImportFormat = ImportFormat.BC) :
        this(
            portfolio,
            importFormat,
            callerRef =
            CallerRef(row[0], row[1], row[2]),
            message = "",
            row = row
        )
}
