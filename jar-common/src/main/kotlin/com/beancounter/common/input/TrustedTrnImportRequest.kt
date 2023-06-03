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
    override var message: String = "",
    val row: List<String> = emptyList(),
    var callerRef: CallerRef = CallerRef(portfolio.owner.id, row[1], row[2]),
) : TrnImport
